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

  // UI Layout constants
  private static final double TEXTBOX_HEIGHT_RATIO = 0.25;
  private static final double TEXTBOX_PADDING = 20;
  private static final double NAME_BOX_HEIGHT = 40;
  private static final Color TEXTBOX_COLOR = Color.rgb(0, 0, 0, 0.8);
  private static final Color NAME_BOX_COLOR = Color.rgb(30, 30, 50, 0.9);
  private static final Color TEXT_COLOR = Color.WHITE;
  private static final Color CHOICE_BG_COLOR = Color.rgb(50, 50, 70, 0.9);
  private static final Color CHOICE_HOVER_COLOR = Color.rgb(70, 70, 100, 0.9);
  private static final Color CHOICE_DISABLED_COLOR = Color.rgb(60, 60, 60, 0.6);
  private static final Color TEXT_COLOR_DISABLED = Color.color(1, 1, 1, 0.5);

  public VnRenderer(GraphicsContext gc) {
    this.gc = gc;
    this.nameFont = Font.font("Arial", FontWeight.BOLD, 18);
    this.dialogueFont = Font.font("Arial", FontWeight.NORMAL, 16);
    this.choiceFont = Font.font("Arial", FontWeight.NORMAL, 16);
  }

  // Optional base directory used to resolve asset paths from filesystem (editor preview)
  private File projectRoot;
  public void setProjectRoot(File root) { this.projectRoot = root; }

  private void renderHistoryOverlay(VnState state, double width, double height) {
    gc.setFill(Color.rgb(0, 0, 0, 0.75));
    gc.fillRect(0, 0, width, height);

    gc.setFill(Color.WHITE);
    gc.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
    double y = 40;
    int linesPerPage = (int) Math.max(1, (height - 120) / 28);
    java.util.List<VnHistory.HistoryEntry> list = state.getHistory().getEntries();
    int total = list.size();
    int offset = Math.max(0, state.getHistoryScroll());
    int startIdx = Math.max(0, total - 1 - offset);
    int drawn = 0;
    for (int i = startIdx; i >= 0 && drawn < linesPerPage; i--) {
      VnHistory.HistoryEntry e = list.get(i);
      String speaker = e.getSpeaker() != null && !e.getSpeaker().isEmpty() ? e.getSpeaker() + ": " : "";
      String line = speaker + e.getText();
      gc.fillText(line, 40, y);
      y += 28;
      drawn++;
    }
    // Scrollbar indicator
    int maxOffset = Math.max(0, total - linesPerPage);
    if (maxOffset > 0) {
      double trackX = width - 28;
      double trackY = 30;
      double trackH = height - 80;
      double trackW = 8;
      gc.setFill(Color.rgb(255,255,255,0.15));
      gc.fillRoundRect(trackX, trackY, trackW, trackH, 6, 6);

      double thumbFrac = Math.max(0.08, Math.min(1.0, (double) linesPerPage / (double) total));
      double thumbH = trackH * thumbFrac;
      int effOffset = Math.min(offset, maxOffset);
      double posFrac = maxOffset == 0 ? 0.0 : (double) effOffset / (double) maxOffset;
      double thumbY = trackY + (trackH - thumbH) * posFrac;
      gc.setFill(Color.rgb(255,255,255,0.7));
      gc.fillRoundRect(trackX, thumbY, trackW, thumbH, 6, 6);
    }

    // Hints
    gc.setFill(Color.rgb(220,220,220,0.9));
    gc.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
    String hint = "Esc: Close    Up/Down: Scroll    PgUp/PgDn: Faster";
    gc.fillText(hint, 40, height - 30);
  }

  /**
   * Render the complete VN scene
   */
  public void render(VnState state, VnScenario scenario, double width, double height) {
    this.currentState = state;
    // Clear screen
    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, width, height);

    boolean didCrossfade = false;
    if (state.getActiveTransition() != null && state.getActiveTransition().getType() == com.jvn.core.vn.VnTransition.TransitionType.CROSSFADE) {
      String prevId = state.getPreviousBackgroundIdDuringTransition();
      String curId = state.getCurrentBackgroundId();
      if (prevId != null && curId != null) {
        renderCrossfadeBackground(scenario.getBackground(prevId), scenario.getBackground(curId), state.getTransitionProgress(), width, height);
        didCrossfade = true;
      }
    }
    if (!didCrossfade) {
      if (state.getCurrentBackgroundId() != null) {
        VnBackground bg = scenario.getBackground(state.getCurrentBackgroundId());
        if (bg != null) {
          renderBackground(bg, width, height);
        }
      }
    }

    // Apply transition effect if active
    if (state.getActiveTransition() != null) {
      renderTransition(state, width, height);
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
      
      VnCharacter character = scenario.getCharacter(slot.getCharacterId());
      if (character != null) {
        String imagePath = character.getExpressionPath(slot.getExpression());
        if (imagePath != null) {
          renderCharacterSprite(imagePath, position, width, height);
        }
      }
    }
  }

  private void renderCharacterSprite(String imagePath, CharacterPosition position, double width, double height) {
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
      gc.fillRoundRect(x, y, spriteWidth, spriteHeight, 20, 20);
      gc.setStroke(Color.WHITE);
      gc.setLineWidth(2);
      gc.strokeRoundRect(x, y, spriteWidth, spriteHeight, 20, 20);
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
    gc.drawImage(img, x, y, spriteWidth, spriteHeight);
  }

  private void renderDialogue(DialogueLine dialogue, VnState state, double width, double height) {
    if (dialogue == null) return;

    double textBoxHeight = height * TEXTBOX_HEIGHT_RATIO;
    double textBoxY = height - textBoxHeight;

    // Draw text box background
    gc.setFill(TEXTBOX_COLOR);
    gc.fillRect(0, textBoxY, width, textBoxHeight);

    // Draw name box if speaker exists
    if (dialogue.getSpeakerName() != null && !dialogue.getSpeakerName().isEmpty()) {
      gc.setFill(NAME_BOX_COLOR);
      gc.fillRect(TEXTBOX_PADDING, textBoxY - NAME_BOX_HEIGHT, 200, NAME_BOX_HEIGHT);
      
      gc.setFill(TEXT_COLOR);
      gc.setFont(nameFont);
      gc.fillText(dialogue.getSpeakerName(), TEXTBOX_PADDING + 10, textBoxY - 15);
    }

    // Draw dialogue text (with reveal animation)
    gc.setFill(TEXT_COLOR);
    gc.setFont(dialogueFont);
    String fullText = dialogue.getText();
    int revealedLength = Math.min(state.getTextRevealProgress(), fullText.length());
    String visibleText = fullText.substring(0, revealedLength);
    
    drawWrappedText(visibleText, TEXTBOX_PADDING, textBoxY + TEXTBOX_PADDING + 20, 
                    width - TEXTBOX_PADDING * 2, dialogueFont);

    // Draw continue indicator if text is fully revealed
    if (revealedLength >= fullText.length() && state.isWaitingForInput()) {
      drawContinueIndicator(width - 30, height - 20);
    }
  }

  private void renderChoices(List<Choice> choices, double width, double height, int hoverIndex) {
    if (choices == null || choices.isEmpty()) return;

    double choiceHeight = 50;
    double choiceWidth = width * 0.6;
    double choiceX = (width - choiceWidth) / 2;
    double totalHeight = choices.size() * (choiceHeight + 10);
    double startY = (height - totalHeight) / 2;

    for (int i = 0; i < choices.size(); i++) {
      Choice choice = choices.get(i);
      double y = startY + i * (choiceHeight + 10);
      boolean enabled = choice.isEnabled() && choiceConditionSatisfied(choice);
      Color bg = !enabled ? CHOICE_DISABLED_COLOR : (i == hoverIndex ? CHOICE_HOVER_COLOR : CHOICE_BG_COLOR);
      // Background
      gc.setFill(bg);
      gc.fillRoundRect(choiceX, y, choiceWidth, choiceHeight, 10, 10);

      // Border
      gc.setStroke(TEXT_COLOR);
      gc.setLineWidth(2);
      gc.strokeRoundRect(choiceX, y, choiceWidth, choiceHeight, 10, 10);

      // Text
      gc.setFill(enabled ? TEXT_COLOR : TEXT_COLOR_DISABLED);
      gc.setFont(choiceFont);
      gc.fillText(choice.getText(), choiceX + 20, y + choiceHeight / 2 + 5);
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

  private void renderTransition(VnState state, double width, double height) {
    float progress = state.getTransitionProgress();
    
    // Fade effect: black overlay with opacity based on progress
    if (state.getActiveTransition().getType() == com.jvn.core.vn.VnTransition.TransitionType.FADE) {
      double opacity = 1.0 - progress; // Fade out from 1.0 to 0.0
      gc.setFill(Color.rgb(0, 0, 0, opacity));
      gc.fillRect(0, 0, width, height);
    }
    // Dissolve is similar to fade but could have different timing
    else if (state.getActiveTransition().getType() == com.jvn.core.vn.VnTransition.TransitionType.DISSOLVE) {
      double opacity = 1.0 - progress;
      gc.setFill(Color.rgb(0, 0, 0, opacity * 0.8)); // Slightly lighter
      gc.fillRect(0, 0, width, height);
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

  private void drawContinueIndicator(double x, double y) {
    gc.setFill(TEXT_COLOR);
    gc.fillPolygon(
      new double[]{x, x + 10, x + 5},
      new double[]{y, y, y + 10},
      3
    );
  }

  public int getHoveredChoiceIndex(List<Choice> choices, double width, double height, double mouseX, double mouseY) {
    if (choices == null || choices.isEmpty()) return -1;

    double choiceHeight = 50;
    double choiceWidth = width * 0.6;
    double choiceX = (width - choiceWidth) / 2;
    double totalHeight = choices.size() * (choiceHeight + 10);
    double startY = (height - totalHeight) / 2;

    for (int i = 0; i < choices.size(); i++) {
      double y = startY + i * (choiceHeight + 10);
      if (mouseX >= choiceX && mouseX <= choiceX + choiceWidth &&
          mouseY >= y && mouseY <= y + choiceHeight) {
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
