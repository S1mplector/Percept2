package com.jvn.fx.vn;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.localization.Localization;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnHistory;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;
import com.jvn.core.vn.text.TextEffect;
import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Renders visual novel elements using JavaFX Canvas
 */
public class VnRenderer {
  private final GraphicsContext gc;
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Font nameFont;
  private final Font dialogueFont;
  private final Font choiceFont;
  private VnState currentState;
  private long animationTime = 0;
  private VnUiLayoutSpec uiLayout;

  // UI Colors
  private static final Color TEXTBOX_COLOR = Color.rgb(0, 0, 0, 0.8);
  private static final Color NAME_BOX_COLOR = Color.rgb(30, 30, 50, 0.9);
  private static final Color TEXT_COLOR = Color.WHITE;
  private static final Color CHOICE_BG_COLOR = Color.rgb(50, 50, 70, 0.9);
  private static final Color CHOICE_HOVER_COLOR = Color.rgb(70, 70, 100, 0.9);
  private static final Color CHOICE_DISABLED_COLOR = Color.rgb(60, 60, 60, 0.6);
  private static final Color TEXT_COLOR_DISABLED = Color.color(1, 1, 1, 0.5);
  private static final Color HISTORY_PANEL_COLOR = Color.rgb(12, 12, 18, 0.92);
  private static final Color HISTORY_BORDER_COLOR = Color.rgb(220, 220, 255, 0.18);
  private static final Color HISTORY_ENTRY_BG = Color.rgb(24, 24, 34, 0.75);
  private static final Color HISTORY_ENTRY_ALT_BG = Color.rgb(18, 18, 26, 0.7);
  private static final Color HISTORY_HINT_COLOR = Color.rgb(210, 210, 220, 0.85);
  private static final double HISTORY_MARGIN = 30;
  private static final double HISTORY_HEADER_HEIGHT = 46;
  private static final double HISTORY_FOOTER_HEIGHT = 44;
  private static final double HISTORY_LINE_HEIGHT = 28;

  public VnRenderer(GraphicsContext gc) {
    this.gc = gc;
    this.nameFont = Font.font("Arial", FontWeight.BOLD, 18);
    this.dialogueFont = Font.font("Arial", FontWeight.NORMAL, 16);
    this.choiceFont = Font.font("Arial", FontWeight.NORMAL, 16);
    this.uiLayout = VnUiLayoutLoader.loadFromAssets();
  }

  // Optional base directory used to resolve asset paths from filesystem (editor preview)
  private File projectRoot;
  public void setProjectRoot(File root) {
    this.projectRoot = root;
    reloadUiLayout();
  }

  public VnUiLayoutSpec getUiLayout() {
    return uiLayout;
  }

  public void setUiLayout(VnUiLayoutSpec layout) {
    this.uiLayout = layout == null ? VnUiLayoutSpec.defaults() : layout;
  }

  public void reloadUiLayout() {
    if (projectRoot != null) {
      this.uiLayout = VnUiLayoutLoader.loadFromProjectRoot(projectRoot);
    } else {
      this.uiLayout = VnUiLayoutLoader.loadFromAssets();
    }
  }

  private void renderHistoryOverlay(VnState state, double width, double height) {
    gc.setFill(Color.rgb(0, 0, 0, 0.7));
    gc.fillRect(0, 0, width, height);

    double panelX = HISTORY_MARGIN;
    double panelY = HISTORY_MARGIN;
    double panelW = width - HISTORY_MARGIN * 2;
    double panelH = height - HISTORY_MARGIN * 2;
    gc.setFill(HISTORY_PANEL_COLOR);
    gc.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);
    gc.setStroke(HISTORY_BORDER_COLOR);
    gc.setLineWidth(1.2);
    gc.strokeRoundRect(panelX, panelY, panelW, panelH, 18, 18);

    double contentX = panelX + 24;
    double contentY = panelY + HISTORY_HEADER_HEIGHT;
    double contentW = panelW - 48;
    double contentH = panelH - HISTORY_HEADER_HEIGHT - HISTORY_FOOTER_HEIGHT;
    int linesPerPage = getHistoryLinesPerPage(height);

    List<VnHistory.HistoryEntry> entries = state.getHistory().getEntries();
    int total = entries.size();
    int maxOffset = Math.max(0, total - linesPerPage);
    int requestedOffset = Math.max(0, state.getHistoryScroll());
    int effectiveOffset = Math.min(requestedOffset, maxOffset);
    int startIdx = Math.max(0, total - 1 - effectiveOffset);

    gc.setFont(Font.font("Arial", FontWeight.BOLD, 18));
    gc.setFill(Color.WHITE);
    gc.fillText(Localization.t("history.title"), panelX + 22, panelY + 30);

    int totalPages = maxOffset == 0 ? 1 : (maxOffset / linesPerPage) + 1;
    int currentPage = maxOffset == 0 ? 1 : (effectiveOffset / linesPerPage) + 1;
    String pageText = "Page " + currentPage + " / " + totalPages;
    double pageTextW = computeTextWidth(pageText, gc.getFont());
    gc.fillText(pageText, panelX + panelW - 22 - pageTextW, panelY + 30);

    gc.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
    int drawn = 0;
    for (int i = startIdx; i >= 0 && drawn < linesPerPage; i--) {
      VnHistory.HistoryEntry entry = entries.get(i);
      String speakerPrefix = entry.getSpeaker() != null && !entry.getSpeaker().isEmpty() ? entry.getSpeaker() + ": " : "";
      String line = speakerPrefix + entry.getText();
      String truncated = truncateText(line, contentW - 16, gc.getFont());
      double y = contentY + drawn * HISTORY_LINE_HEIGHT;
      gc.setFill((drawn % 2 == 0) ? HISTORY_ENTRY_BG : HISTORY_ENTRY_ALT_BG);
      gc.fillRoundRect(contentX - 6, y - 18, contentW + 12, 24, 8, 8);
      gc.setFill(Color.WHITE);
      gc.fillText(truncated, contentX, y);
      drawn++;
    }

    if (total == 0) {
      gc.setFill(Color.rgb(150, 150, 150, 0.7));
      gc.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
      gc.fillText(Localization.t("history.empty"), contentX, contentY + 30);
    }

    if (maxOffset > 0) {
      double trackX = panelX + panelW - 18;
      double trackY = contentY - 10;
      double trackH = contentH + 12;
      double trackW = 6;
      gc.setFill(Color.rgb(255, 255, 255, 0.12));
      gc.fillRoundRect(trackX, trackY, trackW, trackH, 6, 6);

      double thumbFrac = Math.max(0.08, Math.min(1.0, (double) linesPerPage / (double) total));
      double thumbH = trackH * thumbFrac;
      double posFrac = (double) effectiveOffset / (double) maxOffset;
      double thumbY = trackY + (trackH - thumbH) * posFrac;
      gc.setFill(Color.rgb(150, 200, 255, 0.8));
      gc.fillRoundRect(trackX, thumbY, trackW, thumbH, 4, 4);
    }

    gc.setFill(HISTORY_HINT_COLOR);
    gc.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
    gc.fillText(Localization.t("history.hint"), panelX + 20, panelY + panelH - 16);

    String countText = String.format("%d / %d", Math.min(effectiveOffset + 1, total), total);
    double countWidth = computeTextWidth(countText, gc.getFont());
    gc.fillText(countText, panelX + panelW - countWidth - 20, panelY + panelH - 16);
  }

  private void renderSaveSlotOverlay(VnState state, double width, double height) {
    // Semi-transparent backdrop
    gc.setFill(Color.rgb(0, 0, 0, 0.85));
    gc.fillRect(0, 0, width, height);
    
    // Panel dimensions
    double panelW = Math.min(600, width * 0.7);
    double panelH = Math.min(450, height * 0.75);
    double panelX = (width - panelW) / 2;
    double panelY = (height - panelH) / 2;
    
    // Panel background
    gc.setFill(Color.rgb(25, 25, 40, 0.98));
    gc.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);
    
    // Panel border
    gc.setStroke(Color.rgb(80, 80, 120, 0.8));
    gc.setLineWidth(2);
    gc.strokeRoundRect(panelX, panelY, panelW, panelH, 16, 16);
    
    // Title
    boolean isSaveMode = state.isSaveSlotOverlaySaveMode();
    String title = Localization.t(isSaveMode ? "save_slots.title" : "load_slots.title");
    gc.setFill(Color.WHITE);
    gc.setFont(Font.font("Arial", FontWeight.BOLD, 22));
    double titleWidth = computeTextWidth(title, gc.getFont());
    gc.fillText(title, panelX + (panelW - titleWidth) / 2, panelY + 35);
    
    // Slots grid (2 columns x 5 rows)
    double slotW = (panelW - 60) / 2;
    double slotH = 55;
    double startX = panelX + 20;
    double startY = panelY + 60;
    double gapX = 20;
    double gapY = 12;
    
    int selected = state.getSaveSlotSelected();
    
    for (int i = 0; i < 10; i++) {
      int col = i % 2;
      int row = i / 2;
      double slotX = startX + col * (slotW + gapX);
      double slotY = startY + row * (slotH + gapY);
      
      boolean isSelected = (i == selected);
      boolean hasData = hasSaveSlotData(i);
      
      // Slot background
      if (isSelected) {
        gc.setFill(Color.rgb(60, 80, 140, 0.9));
      } else {
        gc.setFill(Color.rgb(40, 40, 60, 0.8));
      }
      gc.fillRoundRect(slotX, slotY, slotW, slotH, 8, 8);
      
      // Selection border
      if (isSelected) {
        gc.setStroke(Color.rgb(150, 200, 255, 1.0));
        gc.setLineWidth(2);
        gc.strokeRoundRect(slotX, slotY, slotW, slotH, 8, 8);
      }
      
      // Slot label
      String slotLabel = i == 0 ? "Quick Save" : (Localization.t("save_slots.slot") + " " + i);
      gc.setFill(isSelected ? Color.WHITE : Color.rgb(200, 200, 200));
      gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
      gc.fillText(slotLabel, slotX + 12, slotY + 22);
      
      // Slot status/timestamp
      String status = hasData ? getSaveSlotTimestamp(i) : Localization.t("save_slots.empty");
      gc.setFill(hasData ? Color.rgb(180, 220, 180) : Color.rgb(120, 120, 120));
      gc.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
      gc.fillText(status, slotX + 12, slotY + 42);
    }
    
    // Hint bar
    gc.setFill(Color.rgb(40, 40, 60, 0.95));
    gc.fillRoundRect(panelX, panelY + panelH - 45, panelW, 45, 0, 0);
    // Round only bottom corners
    gc.fillRoundRect(panelX, panelY + panelH - 20, panelW, 20, 16, 16);
    
    gc.setFill(Color.rgb(180, 180, 180, 0.9));
    gc.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
    String hint = Localization.t(isSaveMode ? "save_slots.hint" : "load_slots.hint");
    gc.fillText(hint, panelX + 20, panelY + panelH - 18);
  }
  
  private boolean hasSaveSlotData(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    String saveDir = System.getProperty("user.home") + "/.jvn/saves";
    java.io.File f = new java.io.File(saveDir, slotName + ".sav");
    return f.exists();
  }
  
  private String getSaveSlotTimestamp(int slot) {
    String slotName = slot == 0 ? "_quicksave" : ("slot_" + slot);
    String saveDir = System.getProperty("user.home") + "/.jvn/saves";
    java.io.File f = new java.io.File(saveDir, slotName + ".sav");
    if (!f.exists()) return Localization.t("save_slots.empty");
    try {
      long millis = f.lastModified();
      java.time.Instant inst = java.time.Instant.ofEpochMilli(millis);
      java.time.ZonedDateTime z = java.time.ZonedDateTime.ofInstant(inst, java.time.ZoneId.systemDefault());
      return z.toLocalDate().toString() + " " + z.toLocalTime().withNano(0).toString();
    } catch (Exception e) {
      return "Saved";
    }
  }

  /**
   * Render the complete VN scene
   */
  public void render(VnState state, VnScenario scenario, double width, double height) {
    this.currentState = state;
    // Clear screen
    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, width, height);

    double shakeMagnitude = state.getScreenShakeMagnitude();
    boolean shaking = shakeMagnitude > 0.01;
    if (shaking) {
      double t = System.currentTimeMillis() * 0.02;
      double shakeX = Math.sin(t * 2.3) * shakeMagnitude;
      double shakeY = Math.cos(t * 1.7) * shakeMagnitude;
      gc.save();
      gc.translate(shakeX, shakeY);
    }

    boolean handledTransitionBackground = false;
    var transition = state.getActiveTransition();
    if (transition != null) {
      switch (transition.getType()) {
        case CROSSFADE -> {
          String prevId = state.getPreviousBackgroundIdDuringTransition();
          String curId = state.getCurrentBackgroundId();
          if (prevId != null && curId != null) {
            renderCrossfadeBackground(scenario.getBackground(prevId), scenario.getBackground(curId), state.getTransitionProgress(), width, height);
            handledTransitionBackground = true;
          }
        }
        case SLIDE_LEFT -> {
          renderSlideBackground(
            scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
            scenario.getBackground(state.getCurrentBackgroundId()),
            state.getTransitionProgress(),
            width, height, true
          );
          handledTransitionBackground = true;
        }
        case SLIDE_RIGHT -> {
          renderSlideBackground(
            scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
            scenario.getBackground(state.getCurrentBackgroundId()),
            state.getTransitionProgress(),
            width, height, false
          );
          handledTransitionBackground = true;
        }
        case WIPE -> {
          renderWipeBackground(
            scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
            scenario.getBackground(state.getCurrentBackgroundId()),
            state.getTransitionProgress(),
            width, height
          );
          handledTransitionBackground = true;
        }
        default -> {
        }
      }
    }
    if (!handledTransitionBackground) {
      if (state.getCurrentBackgroundId() != null) {
        VnBackground bg = scenario.getBackground(state.getCurrentBackgroundId());
        if (bg != null) {
          renderBackground(bg, width, height);
        }
      }
    }

    // Apply transition effect if active
    if (state.getActiveTransition() != null) {
      renderTransitionOverlay(state, width, height);
    }

    // Render characters
    renderCharacters(state, scenario, width, height);

    // Render current node content (unless UI is hidden)
    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden()) {
      switch (currentNode.getType()) {
        case DIALOGUE:
          renderDialogue(currentNode.getDialogue(), state, width, height);
          break;
        case CHOICE:
          renderChoices(currentNode.getChoices(), width, height, -1);
          break;
        case BACKGROUND:
          break;
        case JUMP:
          break;
        case EXTERNAL:
          break;
        case END:
          renderEnd(width, height);
          break;
      }
    }

    // Render mode indicators (always visible)
    renderModeIndicators(state, width, height);

    if (state.isHistoryOverlayShown()) {
      renderHistoryOverlay(state, width, height);
    }

    // Save slot overlay
    if (state.isSaveSlotOverlayShown()) {
      renderSaveSlotOverlay(state, width, height);
    }

    // HUD message (toast)
    long now = System.currentTimeMillis();
    if (state.getHudMessage() != null && now < state.getHudMessageExpireAt()) {
      gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
      gc.setFill(Color.rgb(0, 0, 0, 0.6));
      double boxW = Math.min(width * 0.6, 360);
      double boxH = 40;
      double bx = (width - boxW) / 2;
      double by = height * 0.1;
      gc.fillRoundRect(bx, by, boxW, boxH, 10, 10);
      gc.setFill(Color.WHITE);
      String msg = state.getHudMessage();
      gc.fillText(msg, bx + 12, by + 25);
    }

    if (shaking) {
      gc.restore();
    }

    renderFlashOverlay(state, width, height);
  }

  /**
   * Render with mouse hover support for choices
   */
  public void render(VnState state, VnScenario scenario, double width, double height, double mouseX, double mouseY) {
    this.currentState = state;
    render(state, scenario, width, height);
    
    // Re-render choices with hover effect (if UI not hidden)
    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden() && currentNode.getType() == VnNodeType.CHOICE) {
      int hoverIndex = getHoveredChoiceIndex(currentNode.getChoices(), width, height, mouseX, mouseY);
      renderChoices(currentNode.getChoices(), width, height, hoverIndex);
    }
  }

  private void renderBackground(VnBackground background, double width, double height) {
    Image img = loadImage(background.getImagePath());
    if (img != null) {
      gc.drawImage(img, 0, 0, width, height);
    } else {
      // Placeholder background
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(0, 0, width, height);
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font("Arial", FontWeight.BOLD, 24));
      gc.fillText("No Background Image", 20, 40);
    }
  }

  private void renderCharacters(VnState state, VnScenario scenario, double width, double height) {
    Map<CharacterPosition, VnState.CharacterSlot> characters = state.getVisibleCharacters();
    
    for (Map.Entry<CharacterPosition, VnState.CharacterSlot> entry : characters.entrySet()) {
      CharacterPosition position = entry.getKey();
      VnState.CharacterSlot slot = entry.getValue();
      VnState.CharacterVisual visual = state.getCharacterVisual(position);
      double alpha = visual != null ? visual.getAlpha() : 1.0;
      double offsetX = visual != null ? visual.getOffsetX() : 0.0;
      double offsetY = visual != null ? visual.getOffsetY() : 0.0;
      
      VnCharacter character = scenario.getCharacter(slot.getCharacterId());
      if (character != null) {
        String imagePath = character.getExpressionPath(slot.getExpression());
        if (imagePath != null) {
          gc.save();
          if (alpha < 0.999) gc.setGlobalAlpha(alpha);
          renderCharacterSprite(imagePath, position, width, height, offsetX, offsetY);
          gc.restore();
        }
      }
    }
  }

  private void renderCharacterSprite(String imagePath, CharacterPosition position, double width, double height, double offsetX, double offsetY) {
    Image img = loadImage(imagePath);
    if (img == null) {
      // Draw placeholder silhouette box
      double spriteHeight = height * 0.85;
      double spriteWidth = spriteHeight * 0.5;
      double x = switch (position) {
        case FAR_LEFT -> width * 0.05;
        case LEFT -> width * 0.2;
        case CENTER -> (width - spriteWidth) / 2;
        case RIGHT -> width * 0.8 - spriteWidth;
        case FAR_RIGHT -> width * 0.95 - spriteWidth;
      };
      // Position placeholder so feet are at screen bottom
      double y = height - spriteHeight;
      gc.setFill(Color.rgb(200, 200, 200, 0.4));
      gc.fillRoundRect(x + offsetX, y + offsetY, spriteWidth, spriteHeight, 20, 20);
      gc.setStroke(Color.WHITE);
      gc.setLineWidth(2);
      gc.strokeRoundRect(x + offsetX, y + offsetY, spriteWidth, spriteHeight, 20, 20);
      return;
    }

    double spriteHeight = height * 0.85; // Characters take up 85% of screen height
    double spriteWidth = img.getWidth() * (spriteHeight / img.getHeight());
    
    double x = switch (position) {
      case FAR_LEFT -> width * 0.05;
      case LEFT -> width * 0.2;
      case CENTER -> (width - spriteWidth) / 2;
      case RIGHT -> width * 0.8 - spriteWidth;
      case FAR_RIGHT -> width * 0.95 - spriteWidth;
    };
    
    // Position sprite so feet are at screen bottom (textbox overlaps legs)
    double y = height - spriteHeight;
    gc.drawImage(img, x + offsetX, y + offsetY, spriteWidth, spriteHeight);
  }

  private void renderDialogue(DialogueLine dialogue, VnState state, double width, double height) {
    if (dialogue == null) return;

    double textBoxX = clamp(width * uiLayout.textBoxX(), 0, width);
    double textBoxY = clamp(height * uiLayout.textBoxY(), 0, height);
    double maxBoxWidth = Math.max(1, width - textBoxX);
    double maxBoxHeight = Math.max(1, height - textBoxY);
    double textBoxWidth = clamp(width * uiLayout.textBoxWidth(), 1, maxBoxWidth);
    double textBoxHeight = clamp(height * uiLayout.textBoxHeight(), 1, maxBoxHeight);

    // Draw text box background
    gc.setFill(TEXTBOX_COLOR);
    gc.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);

    // Draw name box if speaker exists
    String speakerName = resolveRuntimeText(dialogue.getSpeakerName());
    if (speakerName != null && !speakerName.isEmpty()) {
      double nameBoxX = textBoxX + uiLayout.nameBoxXOffset();
      double nameBoxY = textBoxY + uiLayout.nameBoxYOffset();
      gc.setFill(NAME_BOX_COLOR);
      gc.fillRect(nameBoxX, nameBoxY, uiLayout.nameBoxWidth(), uiLayout.nameBoxHeight());

      gc.setFill(TEXT_COLOR);
      gc.setFont(nameFont);
      gc.fillText(
          speakerName,
          nameBoxX + uiLayout.nameTextXOffset(),
          nameBoxY + uiLayout.nameTextBaselineOffset()
      );
    }

    // Parse and render dialogue text with effects
    String fullText = resolveRuntimeText(dialogue.getText());
    List<TextSpan> spans = TextParser.parse(fullText);
    int plainLength = TextParser.plainLength(fullText);
    int revealedLength = Math.min(state.getTextRevealProgress(), plainLength);

    double textX = textBoxX + uiLayout.dialogueTextHorizontalPadding();
    double textY = textBoxY + uiLayout.dialogueTextTopPadding();
    double textWidth = Math.max(60, textBoxWidth - uiLayout.dialogueTextHorizontalPadding() * 2);
    drawStyledText(spans, revealedLength, textX, textY, textWidth);

    // Draw continue indicator if text is fully revealed
    if (revealedLength >= plainLength && state.isWaitingForInput()) {
      drawContinueIndicator(textBoxX + textBoxWidth - 30, textBoxY + textBoxHeight - 20);
    }
  }

  private void drawStyledText(List<TextSpan> spans, int revealedChars, double startX, double startY, double maxWidth) {
    gc.setFont(dialogueFont);
    double x = startX;
    double y = startY;
    double lineHeight = 22;
    int charCount = 0;

    for (TextSpan span : spans) {
      String text = span.getText();
      int spanLen = text.length();
      
      // Calculate how many chars of this span to show
      int visibleChars = 0;
      if (charCount < revealedChars) {
        visibleChars = Math.min(spanLen, revealedChars - charCount);
      }
      
      if (visibleChars > 0) {
        String visibleText = text.substring(0, visibleChars);
        
        // Apply color if specified
        if (span.hasColor()) {
          gc.setFill(parseColorHex(span.getColorHex()));
        } else {
          gc.setFill(TEXT_COLOR);
        }
        
        // Apply font style for bold/italic
        Font effectFont = dialogueFont;
        if (span.getEffect() == TextEffect.BOLD) {
          effectFont = Font.font(dialogueFont.getFamily(), FontWeight.BOLD, dialogueFont.getSize());
        } else if (span.getEffect() == TextEffect.ITALIC) {
          effectFont = Font.font(dialogueFont.getFamily(), FontWeight.NORMAL, dialogueFont.getSize());
        }
        gc.setFont(effectFont);
        
        // Draw each character with effects
        for (int i = 0; i < visibleText.length(); i++) {
          char c = visibleText.charAt(i);
          double charWidth = computeTextWidth(String.valueOf(c), effectFont);
          
          // Check for line wrap
          if (x + charWidth > startX + maxWidth) {
            x = startX;
            y += lineHeight;
          }
          
          // Apply effect offset
          double offsetX = 0, offsetY = 0;
          double effectPhase = (animationTime * 0.01) + (charCount + i) * 0.3;
          
          switch (span.getEffect()) {
            case SHAKE -> {
              offsetX = (Math.random() - 0.5) * 3;
              offsetY = (Math.random() - 0.5) * 3;
            }
            case WAVE -> {
              offsetY = Math.sin(effectPhase) * 3;
            }
            case BOUNCE -> {
              offsetY = Math.abs(Math.sin(effectPhase * 2)) * -4;
            }
            case RAINBOW -> {
              double hue = (effectPhase * 50) % 360;
              gc.setFill(Color.hsb(hue, 0.8, 1.0));
            }
            default -> {}
          }
          
          gc.fillText(String.valueOf(c), x + offsetX, y + offsetY);
          x += charWidth;
        }
        
        // Reset font after span
        gc.setFont(dialogueFont);
      }
      
      charCount += spanLen;
    }
  }

  private Color parseColorHex(String hex) {
    if (hex == null || hex.isEmpty()) return TEXT_COLOR;
    try {
      String h = hex.startsWith("#") ? hex.substring(1) : hex;
      if (h.length() == 6) {
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return Color.rgb(r, g, b);
      }
    } catch (Exception ignored) {}
    return TEXT_COLOR;
  }

  private void renderChoices(List<Choice> choices, double width, double height, int hoverIndex) {
    if (choices == null || choices.isEmpty()) return;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), width, height);

    for (int i = 0; i < choices.size(); i++) {
      Choice choice = choices.get(i);
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      boolean enabled = choice.isEnabled() && choiceConditionSatisfied(choice);
      Color bg = !enabled ? CHOICE_DISABLED_COLOR : (i == hoverIndex ? CHOICE_HOVER_COLOR : CHOICE_BG_COLOR);
      // Background
      gc.setFill(bg);
      gc.fillRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), 10, 10);

      // Border
      gc.setStroke(TEXT_COLOR);
      gc.setLineWidth(2);
      gc.strokeRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), 10, 10);

      // Text
      gc.setFill(enabled ? TEXT_COLOR : TEXT_COLOR_DISABLED);
      gc.setFont(choiceFont);
      gc.fillText(
          resolveRuntimeText(choice.getText()),
          geo.choiceX() + uiLayout.choiceTextXPadding(),
          y + geo.choiceHeight() / 2 + 5
      );
    }
  }

  private boolean choiceConditionSatisfied(Choice c) {
    String cond = c.getCondition();
    if (cond == null || cond.isEmpty()) return true;
    String[] toks = cond.trim().split("\\s+");
    if (toks.length < 3) return true;
    Object lhs = null;
    if (toks.length >= 1) lhs = getVariableSafe(toks[0]);
    String op = toks.length >= 2 ? toks[1] : "==";
    String rhsRaw = toks.length >= 3 ? toks[2] : "";
    Object rhs = parseScalar(rhsRaw);
    if (lhs instanceof Number ln && rhs instanceof Number rn) {
      double a = ln.doubleValue();
      double b = rn.doubleValue();
      if ("==".equals(op)) return a == b;
      if ("!=".equals(op)) return a != b;
      if (">".equals(op)) return a > b;
      if ("<".equals(op)) return a < b;
      if (">=".equals(op)) return a >= b;
      if ("<=".equals(op)) return a <= b;
      return false;
    }
    String a = lhs == null ? "" : lhs.toString();
    String b = rhs == null ? "" : rhs.toString();
    if ("==".equals(op)) return a.equals(b);
    if ("!=".equals(op)) return !a.equals(b);
    return false;
  }

  private Object getVariableSafe(String key) {
    return key == null ? null : currentState != null ? currentState.getVariables().get(key) : null;
  }

  private static Object parseScalar(String s) {
    if (s == null) return "";
    String t = s.trim();
    if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
    try { if (t.contains(".")) return Double.parseDouble(t); else return Integer.parseInt(t); }
    catch (Exception ignored) {}
    return t;
  }

  private void renderEnd(double width, double height) {
    gc.setFill(TEXT_COLOR);
    gc.setFont(Font.font("Arial", FontWeight.BOLD, 32));
    String text = "End";
    gc.fillText(text, width / 2 - 30, height / 2);
  }

  private void renderTransitionOverlay(VnState state, double width, double height) {
    if (state.getActiveTransition() == null) return;
    float progress = state.getTransitionProgress();
    var transitionType = state.getActiveTransition().getType();
    
    switch (transitionType) {
      case FADE -> {
        // Fade effect: black overlay with opacity based on progress
        double opacity = 1.0 - progress; // Fade out from 1.0 to 0.0
        gc.setFill(Color.rgb(0, 0, 0, opacity));
        gc.fillRect(0, 0, width, height);
      }
      case DISSOLVE -> {
        // Dissolve: smoother fade with easing
        double eased = easeInOutQuad(progress);
        double opacity = 1.0 - eased;
        gc.setFill(Color.rgb(0, 0, 0, opacity * 0.85));
        gc.fillRect(0, 0, width, height);
      }
      case SLIDE_LEFT -> {
        // Slide from right: black panel slides off to left
        double eased = easeOutCubic(progress);
        double panelX = -width * eased;
        gc.setFill(Color.BLACK);
        gc.fillRect(panelX, 0, width, height);
      }
      case SLIDE_RIGHT -> {
        // Slide from left: black panel slides off to right
        double eased = easeOutCubic(progress);
        double panelX = width * (1.0 - eased) - width;
        gc.setFill(Color.BLACK);
        gc.fillRect(width - panelX - width, 0, width, height);
      }
      case WIPE -> {
        // Horizontal wipe: black rectangle shrinks from left to right
        double eased = easeInOutQuad(progress);
        double wipeWidth = width * (1.0 - eased);
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, wipeWidth, height);
      }
      case CROSSFADE -> {
        // Crossfade is handled separately in render() for backgrounds
      }
      case NONE -> {
        // No visual effect
      }
    }
  }

  private double easeInOutQuad(double t) {
    return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
  }

  private double easeOutCubic(double t) {
    return 1 - Math.pow(1 - t, 3);
  }

  private double easeInCubic(double t) {
    return t * t * t;
  }

  private void renderSlideBackground(VnBackground prev, VnBackground cur, float progress, double width, double height, boolean left) {
    double p = Math.max(0, Math.min(1, progress));
    double offset = width * p;
    double prevX = left ? -offset : offset;
    double curX = left ? (width - offset) : (-width + offset);
    if (prev != null || cur != null) {
      drawBackgroundAt(prev, prevX, 0, width, height);
      drawBackgroundAt(cur, curX, 0, width, height);
    }
  }

  private void renderWipeBackground(VnBackground prev, VnBackground cur, float progress, double width, double height) {
    drawBackgroundAt(prev, 0, 0, width, height);
    if (cur != null) {
      double p = Math.max(0, Math.min(1, progress));
      double wipeW = width * p;
      gc.save();
      gc.beginPath();
      gc.rect(0, 0, wipeW, height);
      gc.closePath();
      gc.clip();
      drawBackgroundAt(cur, 0, 0, width, height);
      gc.restore();
    }
  }

  private void renderCrossfadeBackground(VnBackground prev, VnBackground cur, float progress, double width, double height) {
    double alphaCur = Math.max(0, Math.min(1, progress));
    double alphaPrev = 1.0 - alphaCur;
    if (prev != null) {
      Image imgPrev = loadImage(prev.getImagePath());
      if (imgPrev != null) {
        gc.setGlobalAlpha(alphaPrev);
        gc.drawImage(imgPrev, 0, 0, width, height);
      }
    }
    if (cur != null) {
      Image imgCur = loadImage(cur.getImagePath());
      if (imgCur != null) {
        gc.setGlobalAlpha(alphaCur);
        gc.drawImage(imgCur, 0, 0, width, height);
      }
    }
    gc.setGlobalAlpha(1.0);
  }

  private void drawBackgroundAt(VnBackground background, double x, double y, double width, double height) {
    if (background == null) {
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(x, y, width, height);
      return;
    }
    Image img = loadImage(background.getImagePath());
    if (img != null) {
      gc.drawImage(img, x, y, width, height);
    } else {
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(x, y, width, height);
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font("Arial", FontWeight.BOLD, 22));
      gc.fillText("No Background Image", x + 20, y + 40);
    }
  }

  private void renderFlashOverlay(VnState state, double width, double height) {
    float alpha = state.getFlashAlpha();
    if (alpha <= 0.001f) return;
    float r = state.getFlashR();
    float g = state.getFlashG();
    float b = state.getFlashB();
    gc.setFill(Color.color(r, g, b, Math.min(1f, alpha)));
    gc.fillRect(0, 0, width, height);
  }

  private void drawWrappedText(String text, double x, double y, double maxWidth, Font font) {
    gc.setFont(font);
    String[] words = text.split(" ");
    StringBuilder line = new StringBuilder();
    double currentY = y;
    double lineHeight = 22;

    for (String word : words) {
      String testLine = line.length() == 0 ? word : line + " " + word;
      double testWidth = computeTextWidth(testLine, font);
      
      if (testWidth > maxWidth && line.length() > 0) {
        gc.fillText(line.toString(), x, currentY);
        line = new StringBuilder(word);
        currentY += lineHeight;
      } else {
        line = new StringBuilder(testLine);
      }
    }
    
    if (line.length() > 0) {
      gc.fillText(line.toString(), x, currentY);
    }
  }

  private double computeTextWidth(String text, Font font) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text(text);
    helper.setFont(font);
    return helper.getLayoutBounds().getWidth();
  }

  private double clamp(double value, double min, double max) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private String truncateText(String text, double maxWidth, Font font) {
    if (text == null) return "";
    if (computeTextWidth(text, font) <= maxWidth) return text;
    String ellipsis = "...";
    double ellipsisWidth = computeTextWidth(ellipsis, font);
    int len = text.length();
    while (len > 0) {
      String base = text.substring(0, len);
      if (computeTextWidth(base, font) + ellipsisWidth <= maxWidth) {
        return base + ellipsis;
      }
      len--;
    }
    return ellipsis;
  }

  public int getHistoryLinesPerPage(double height) {
    double panelH = height - HISTORY_MARGIN * 2;
    double contentH = panelH - HISTORY_HEADER_HEIGHT - HISTORY_FOOTER_HEIGHT;
    return Math.max(1, (int) Math.floor(contentH / HISTORY_LINE_HEIGHT));
  }

  private String resolveRuntimeText(String text) {
    if (text == null) return "";
    if (currentState == null) return text;
    return VnVariableInterpolator.interpolate(text, currentState.getVariables());
  }

  private void drawContinueIndicator(double x, double y) {
    // Bounce animation: offset Y by sine wave
    double bounce = Math.sin(animationTime * 0.005) * 4;
    double animY = y + bounce;
    
    gc.setFill(TEXT_COLOR);
    gc.fillPolygon(
      new double[]{x, x + 10, x + 5},
      new double[]{animY, animY, animY + 10},
      3
    );
  }

  public void updateAnimation(long deltaMs) {
    animationTime += deltaMs;
  }

  public int getHoveredChoiceIndex(List<Choice> choices, double width, double height, double mouseX, double mouseY) {
    if (choices == null || choices.isEmpty()) return -1;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), width, height);

    for (int i = 0; i < choices.size(); i++) {
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      if (mouseX >= geo.choiceX() && mouseX <= geo.choiceX() + geo.choiceWidth() &&
          mouseY >= y && mouseY <= y + geo.choiceHeight()) {
        return i;
      }
    }
    return -1;
  }

  private ChoiceGeometry computeChoiceGeometry(int count, double width, double height) {
    double choiceHeight = Math.max(12, uiLayout.choiceHeight());
    double choiceGap = Math.max(0, uiLayout.choiceGap());
    double choiceWidth = clamp(width * uiLayout.choiceWidthFactor(), 20, width);
    double choiceX = width * uiLayout.choiceXCenter() - choiceWidth / 2.0;
    choiceX = clamp(choiceX, 0, Math.max(0, width - choiceWidth));
    double totalHeight = count * choiceHeight + Math.max(0, count - 1) * choiceGap;
    double startY = uiLayout.choiceYStart() < 0
        ? (height - totalHeight) / 2.0
        : (height * uiLayout.choiceYStart());
    startY = clamp(startY, 0, Math.max(0, height - totalHeight));
    return new ChoiceGeometry(choiceX, startY, choiceWidth, choiceHeight, choiceGap);
  }

  private record ChoiceGeometry(double choiceX, double startY, double choiceWidth, double choiceHeight, double choiceGap) {}

  public int getHoveredSaveSlotIndex(double width, double height, double mouseX, double mouseY) {
    double panelW = Math.min(600, width * 0.7);
    double panelH = Math.min(450, height * 0.75);
    double panelX = (width - panelW) / 2;
    double panelY = (height - panelH) / 2;
    double slotW = (panelW - 60) / 2;
    double slotH = 55;
    double startX = panelX + 20;
    double startY = panelY + 60;
    double gapX = 20;
    double gapY = 12;

    for (int i = 0; i < 10; i++) {
      int col = i % 2;
      int row = i / 2;
      double slotX = startX + col * (slotW + gapX);
      double slotY = startY + row * (slotH + gapY);
      if (mouseX >= slotX && mouseX <= slotX + slotW && mouseY >= slotY && mouseY <= slotY + slotH) {
        return i;
      }
    }
    return -1;
  }

  private Image loadImage(String path) {
    if (path == null) return null;
    
    return imageCache.computeIfAbsent(path, p -> {
      try {
        // Try to load from classpath
        var url = getClass().getClassLoader().getResource(p);
        if (url != null) {
          return new Image(url.toExternalForm());
        }
        // Fallback: filesystem (absolute or relative to project root)
        // 1) Absolute or working-directory-relative
        File f = new File(p);
        if (f.exists()) {
          return new Image(f.toURI().toString());
        }
        // 2) Relative to project root (if provided)
        if (projectRoot != null) {
          // If path starts with the project directory name, strip it
          String normalized = p.replace('\\', '/');
          String rootName = projectRoot.getName();
          if (normalized.startsWith(rootName + "/")) {
            normalized = normalized.substring(rootName.length() + 1);
          }
          File pf = new File(projectRoot, normalized);
          if (pf.exists()) {
            return new Image(pf.toURI().toString());
          }
        }
      } catch (Exception e) {
        System.err.println("Failed to load image: " + path);
      }
      return null;
    });
  }

  private void renderModeIndicators(VnState state, double width, double height) {
    gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
    gc.setFill(Color.rgb(255, 255, 255, 0.9));
    
    double y = 25;
    
    // Skip mode indicator
    if (state.isSkipMode()) {
      gc.fillText(Localization.t("hud.skip"), width - 100, y);
      y += 20;
    }
    
    // Auto-play mode indicator
    if (state.isAutoPlayMode()) {
      gc.fillText(Localization.t("hud.auto"), width - 100, y);
      y += 20;
    }
    
    // UI hidden indicator
    if (state.isUiHidden()) {
      gc.fillText(Localization.t("hud.ui_off"), width - 110, y);
    }
  }

  public void clearCache() {
    imageCache.clear();
  }
}
