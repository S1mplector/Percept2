package com.jvn.fx.vn;

import com.jvn.core.vn.*;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders visual novel elements using JavaFX Canvas
 */
public class VnRenderer {
  private final GraphicsContext gc;
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Font nameFont;
  private final Font dialogueFont;
  private final Font choiceFont;

  // UI Layout constants
  private static final double TEXTBOX_HEIGHT_RATIO = 0.25;
  private static final double TEXTBOX_PADDING = 20;
  private static final double NAME_BOX_HEIGHT = 40;
  private static final Color TEXTBOX_COLOR = Color.rgb(0, 0, 0, 0.8);
  private static final Color NAME_BOX_COLOR = Color.rgb(30, 30, 50, 0.9);
  private static final Color TEXT_COLOR = Color.WHITE;
  private static final Color CHOICE_BG_COLOR = Color.rgb(50, 50, 70, 0.9);
  private static final Color CHOICE_HOVER_COLOR = Color.rgb(70, 70, 100, 0.9);

  public VnRenderer(GraphicsContext gc) {
    this.gc = gc;
    this.nameFont = Font.font("Arial", FontWeight.BOLD, 18);
    this.dialogueFont = Font.font("Arial", FontWeight.NORMAL, 16);
    this.choiceFont = Font.font("Arial", FontWeight.NORMAL, 16);
  }

  /**
   * Render the complete VN scene
   */
  public void render(VnState state, VnScenario scenario, double width, double height) {
    // Clear screen
    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, width, height);

    // Render background
    if (state.getCurrentBackgroundId() != null) {
      VnBackground bg = scenario.getBackground(state.getCurrentBackgroundId());
      if (bg != null) {
        renderBackground(bg, width, height);
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
        case END:
          renderEnd(width, height);
          break;
      }
    }

    // Render mode indicators (always visible)
    renderModeIndicators(state, width, height);
  }

  /**
   * Render with mouse hover support for choices
   */
  public void render(VnState state, VnScenario scenario, double width, double height, double mouseX, double mouseY) {
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
    if (img == null) return;

    double spriteHeight = height * 0.7; // Characters take up 70% of screen height
    double spriteWidth = img.getWidth() * (spriteHeight / img.getHeight());
    
    double x = switch (position) {
      case FAR_LEFT -> width * 0.05;
      case LEFT -> width * 0.2;
      case CENTER -> (width - spriteWidth) / 2;
      case RIGHT -> width * 0.8 - spriteWidth;
      case FAR_RIGHT -> width * 0.95 - spriteWidth;
    };
    
    double y = height - spriteHeight - (height * TEXTBOX_HEIGHT_RATIO);
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

      // Background
      gc.setFill(i == hoverIndex ? CHOICE_HOVER_COLOR : CHOICE_BG_COLOR);
      gc.fillRoundRect(choiceX, y, choiceWidth, choiceHeight, 10, 10);

      // Border
      gc.setStroke(TEXT_COLOR);
      gc.setLineWidth(2);
      gc.strokeRoundRect(choiceX, y, choiceWidth, choiceHeight, 10, 10);

      // Text
      gc.setFill(TEXT_COLOR);
      gc.setFont(choiceFont);
      gc.fillText(choice.getText(), choiceX + 20, y + choiceHeight / 2 + 5);
    }
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
      gc.fillText("⏭ SKIP", width - 100, y);
      y += 20;
    }
    
    // Auto-play mode indicator
    if (state.isAutoPlayMode()) {
      gc.fillText("▶ AUTO", width - 100, y);
      y += 20;
    }
    
    // UI hidden indicator
    if (state.isUiHidden()) {
      gc.fillText("👁 UI OFF", width - 110, y);
    }
  }

  public void clearCache() {
    imageCache.clear();
  }
}
