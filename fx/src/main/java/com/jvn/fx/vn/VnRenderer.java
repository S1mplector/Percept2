package com.jvn.fx.vn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.localization.Localization;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;
import com.jvn.core.vn.text.TextEffect;
import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;

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
  private Font nameFont;
  private Font dialogueFont;
  private Font choiceFont;

  // Default font settings
  private static final String DEFAULT_FONT_FAMILY = "SansSerif";
  private static final int DEFAULT_NAME_FONT_SIZE = 18;
  private static final int DEFAULT_DIALOGUE_FONT_SIZE = 22;
  private static final int DEFAULT_CHOICE_FONT_SIZE = 20;
  private VnState currentState;
  private long animationTime = 0;
  private AudioFacade audioFacade;
  private VnUiLayoutSpec uiLayout;
  private VnUiStyleSpec uiStyle = VnUiStyleSpec.defaults();
  private List<VnUiActionButtonSpec> textBoxButtons = List.of();
  private VnCharacterSceneAccessor timelineAccessor;
  private double styleCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double styleCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;
  private double characterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double characterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;

  public void setTimelineAccessor(VnCharacterSceneAccessor accessor) { this.timelineAccessor = accessor; }
  public void setAudioFacade(AudioFacade facade) { this.audioFacade = facade; }

  // UI Colors
  private static final Color TEXTBOX_COLOR = Color.rgb(12, 18, 32, 0.88);
  private static final Color NAME_BOX_COLOR = Color.rgb(20, 32, 56, 0.56);
  private static final Color TEXT_COLOR = Color.web("#E8EDF6");
  private static final Color CHOICE_BG_COLOR = Color.web("#1A2640D8");
  private static final Color CHOICE_HOVER_COLOR = Color.web("#243358E8");
  private static final Color CHOICE_DISABLED_COLOR = Color.web("#121826A0");
  private static final Color TEXT_COLOR_DISABLED = Color.web("#6878A0");
  private static final Color CHOICE_DISABLED_BORDER_COLOR = Color.web("#28345060");
  private static final double DEFAULT_CHOICE_RADIUS = 8.0;
  private static final double DEFAULT_CHOICE_BORDER_WIDTH = 1.5;
  private static final double DEFAULT_CHOICE_TEXT_BASELINE_OFFSET = 4.0;
  private static final double DEFAULT_CHARACTER_HEIGHT_FACTOR = 0.85;
  private static final double DEFAULT_CHARACTER_BASELINE_Y = 1.0;
  private static final int VISUALIZER_BAR_COUNT = 96;
  private static final long VISUALIZER_STALE_NS = 700_000_000L;
  private static final String VAR_CHARACTER_HEIGHT_FACTOR = "ui.characterHeightFactor";
  private static final String VAR_CHARACTER_BASELINE_Y = "ui.characterBaselineY";
  private static final String VAR_AUDIO_VISUALIZER_ENABLED = "ui.audioVisualizer";
  private static final String VAR_AUDIO_VISUALIZER_BARS = "ui.audioVisualizerBars";

  private Image choiceButtonImage;
  private Image choiceButtonHoverImage;
  private Image choiceButtonDisabledImage;
  private Image textBoxImage;
  private Image nameBoxImage;
  private Color textBoxFillColor = TEXTBOX_COLOR;
  private Color nameBoxFillColor = NAME_BOX_COLOR;
  private Color nameTextFillColor = Color.web("#FFD78A");
  private Color dialogueTextFillColor = TEXT_COLOR;
  private double textBoxAssetOverlayOpacity = 0.28;
  private double nameBoxRenderOpacity = 1.0;
  private Color choiceBgColor = CHOICE_BG_COLOR;
  private Color choiceHoverColor = CHOICE_HOVER_COLOR;
  private Color choiceDisabledColor = CHOICE_DISABLED_COLOR;
  private Color choiceTextColor = TEXT_COLOR;
  private Color choiceHoverTextColor = TEXT_COLOR;
  private Color choiceDisabledTextColor = TEXT_COLOR_DISABLED;
  private Color choiceBorderColor = TEXT_COLOR;
  private Color choiceHoverBorderColor = TEXT_COLOR;
  private Color choiceDisabledBorderColor = CHOICE_DISABLED_BORDER_COLOR;
  private double choiceCornerRadius = DEFAULT_CHOICE_RADIUS;
  private double choiceBorderWidth = DEFAULT_CHOICE_BORDER_WIDTH;
  private double choiceTextBaselineOffset = DEFAULT_CHOICE_TEXT_BASELINE_OFFSET;
  private double nameTextXAlign = 0.0;
  private double dialogueTextXAlign = 0.0;
  private double choiceTextXAlign = 0.0;
  private List<BoundsPointCodec.Point> textBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> nameBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> dialogueTextBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> choiceButtonBoundsPolygon = List.of();
  private final double[] visualizerLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerTargets = new double[VISUALIZER_BAR_COUNT];

  public VnRenderer(GraphicsContext gc) {
    this.gc = gc;
    this.nameFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD, DEFAULT_NAME_FONT_SIZE);
    this.dialogueFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_DIALOGUE_FONT_SIZE);
    this.choiceFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_CHOICE_FONT_SIZE);
    reloadUiLayout();
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
    applyUiStyle(uiStyle);
  }

  public VnUiStyleSpec getUiStyle() {
    return uiStyle;
  }

  public void setUiStyle(VnUiStyleSpec style) {
    this.uiStyle = style == null ? VnUiStyleSpec.defaults() : style;
    applyUiStyle(this.uiStyle);
  }

  public List<VnUiActionButtonSpec> getTextBoxButtons() {
    return textBoxButtons;
  }

  public void setTextBoxButtons(List<VnUiActionButtonSpec> buttons) {
    this.textBoxButtons = buttons == null ? List.of() : List.copyOf(buttons);
  }

  public void reloadUiLayout() {
    VnUiLayoutLoader.LoadResult result = projectRoot != null
        ? VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(projectRoot)
        : VnUiLayoutLoader.loadFromAssetsWithDiagnostics();
    this.uiLayout = result.layout();
    this.uiStyle = result.style();
    this.textBoxButtons = result.textBoxButtons();
    applyUiStyle(this.uiStyle);
  }

  /**
   * Render the complete VN scene
   */
  public void render(VnState state, VnScenario scenario, double width, double height) {
    this.currentState = state;
    applyRuntimeCharacterFramingOverrides(state);
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

    // Audio-reactive layer between background and sprites/UI.
    renderAudioVisualizer(width, height);

    // Render characters
    renderCharacters(state, scenario, width, height);

    // Render current node content (unless UI is hidden)
    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden()) {
      switch (currentNode.getType()) {
        case DIALOGUE:
          renderDialogue(currentNode.getDialogue(), state, width, height, -1);
          break;
        case CHOICE:
          renderChoices(currentNode.getChoices(), width, height, -1);
          break;
        case BACKGROUND:
        case TRANSITION:
        case SHOW:
        case HIDE:
        case MOVE:
        case WAIT:
        case AUDIO:
        case JUMP:
        case CALL:
        case RETURN:
        case EXTERNAL:
          break;
        case END:
          renderEnd(width, height);
          break;
      }
    }

    // Render mode indicators (always visible)
    renderModeIndicators(state, width, height);

    // HUD message (toast)
    long now = System.currentTimeMillis();
    if (state.getHudMessage() != null && now < state.getHudMessageExpireAt()) {
      gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 16));
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
    
    // Re-render choices/buttons with hover effect (if UI not hidden)
    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden()) {
      if (currentNode.getType() == VnNodeType.CHOICE) {
        int hoverIndex = getHoveredChoiceIndex(currentNode.getChoices(), width, height, mouseX, mouseY);
        renderChoices(currentNode.getChoices(), width, height, hoverIndex);
      } else if (currentNode.getType() == VnNodeType.DIALOGUE) {
        int hoverButton = getHoveredTextBoxButtonIndex(state, width, height, mouseX, mouseY);
        renderDialogue(currentNode.getDialogue(), state, width, height, hoverButton);
      }
    }
  }

  private int positionOrdinal(CharacterPosition position) {
    if (position == null) return 0;
    return position.getOrdinal();
  }

  private void renderBackground(VnBackground background, double width, double height) {
    if (background == null) return;
    Image img = loadImage(background.getImagePath());
    com.jvn.core.scene2d.Entity2D proxy = timelineAccessor != null
        ? timelineAccessor.getProxy(background.getId())
        : null;
    if (img != null) {
      drawBackgroundImage(img, proxy, width, height);
    } else {
      // Placeholder background
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(0, 0, width, height);
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 24));
      gc.fillText("No Background Image", 20, 40);
    }
  }

  private void drawBackgroundImage(Image img, com.jvn.core.scene2d.Entity2D proxy, double width, double height) {
    if (proxy != null && !proxy.isVisible()) return;
    double x = proxy != null ? proxy.getX() : 0.0;
    double y = proxy != null ? proxy.getY() : 0.0;
    double rotation = proxy != null ? proxy.getRotationDeg() : 0.0;
    double scaleX = proxy != null ? proxy.getScaleX() : 1.0;
    double scaleY = proxy != null ? proxy.getScaleY() : 1.0;
    boolean transformed = Math.abs(x) > 1e-6
        || Math.abs(y) > 1e-6
        || Math.abs(rotation) > 1e-6
        || Math.abs(scaleX - 1.0) > 1e-6
        || Math.abs(scaleY - 1.0) > 1e-6;

    if (!transformed) {
      gc.drawImage(img, 0, 0, width, height);
      return;
    }

    gc.save();
    gc.translate(x, y);
    if (Math.abs(rotation) > 1e-6) gc.rotate(rotation);
    if (Math.abs(scaleX - 1.0) > 1e-6 || Math.abs(scaleY - 1.0) > 1e-6) gc.scale(scaleX, scaleY);
    gc.drawImage(img, 0, 0, width, height);
    gc.restore();
  }

  private void renderCharacters(VnState state, VnScenario scenario, double width, double height) {
    Map<CharacterPosition, VnState.CharacterSlot> characters = state.getVisibleCharacters();

    java.util.List<Map.Entry<CharacterPosition, VnState.CharacterSlot>> ordered = new java.util.ArrayList<>(characters.entrySet());
    ordered.sort(
        java.util.Comparator
            .comparingInt((Map.Entry<CharacterPosition, VnState.CharacterSlot> e) ->
                e.getValue() != null ? e.getValue().getLayerOrder() : 0)
            .thenComparingInt(e -> positionOrdinal(e.getKey()))
    );

    for (Map.Entry<CharacterPosition, VnState.CharacterSlot> entry : ordered) {
      CharacterPosition position = entry.getKey();
      VnState.CharacterSlot slot = entry.getValue();
      if (slot == null) continue;
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
          renderCharacterSprite(imagePath, position, width, height, offsetX, offsetY, slot.getCharacterId());
          gc.restore();
        }
      }
    }
  }

  private void renderCharacterSprite(String imagePath, CharacterPosition position, double width, double height, double offsetX, double offsetY, String characterId) {
    List<String> layerPaths = parseLayerPaths(imagePath);
    Image reference = firstAvailableImage(layerPaths);

    // If a timeline proxy drives this character, use its absolute position
    if (timelineAccessor != null && characterId != null) {
      com.jvn.core.scene2d.Entity2D proxy = timelineAccessor.getProxy(characterId);
      if (proxy != null && (proxy.getX() != 0 || proxy.getY() != 0)) {
        double spriteHeight = height * characterHeightFactor;
        double spriteWidth = reference != null ? reference.getWidth() * (spriteHeight / reference.getHeight()) : spriteHeight * 0.5;
        double px = proxy.getX();
        double py = proxy.getY();
        if (reference != null) {
          drawLayerStack(layerPaths, px, py, spriteWidth, spriteHeight);
        } else {
          gc.setFill(Color.rgb(200, 200, 200, 0.4));
          gc.fillRoundRect(px, py, spriteWidth, spriteHeight, 20, 20);
        }
        return;
      }
    }
    if (reference == null) {
      // Draw placeholder silhouette box
      double spriteHeight = height * characterHeightFactor;
      double spriteWidth = spriteHeight * 0.5;
      double x = position.computeScreenX(width, spriteWidth);
      double y = position.computeScreenY(height, spriteHeight, characterBaselineY);
      gc.setFill(Color.rgb(200, 200, 200, 0.4));
      gc.fillRoundRect(x + offsetX, y + offsetY, spriteWidth, spriteHeight, 20, 20);
      gc.setStroke(Color.WHITE);
      gc.setLineWidth(2);
      gc.strokeRoundRect(x + offsetX, y + offsetY, spriteWidth, spriteHeight, 20, 20);
      return;
    }

    double spriteHeight = height * characterHeightFactor;
    double spriteWidth = reference.getWidth() * (spriteHeight / reference.getHeight());
    double x = position.computeScreenX(width, spriteWidth);
    double y = position.computeScreenY(height, spriteHeight, characterBaselineY);
    drawLayerStack(layerPaths, x + offsetX, y + offsetY, spriteWidth, spriteHeight);
  }

  private List<String> parseLayerPaths(String imagePathSpec) {
    List<String> layers = new ArrayList<>();
    if (imagePathSpec == null) return layers;
    for (String part : imagePathSpec.split("\\|")) {
      String path = part == null ? "" : part.trim();
      if (!path.isEmpty()) layers.add(path);
    }
    if (layers.isEmpty()) {
      String single = imagePathSpec.trim();
      if (!single.isEmpty()) layers.add(single);
    }
    return layers;
  }

  private Image firstAvailableImage(List<String> layerPaths) {
    if (layerPaths == null) return null;
    for (String path : layerPaths) {
      Image img = loadImage(path);
      if (img != null) return img;
    }
    return null;
  }

  private void drawLayerStack(List<String> layerPaths, double x, double y, double width, double height) {
    if (layerPaths == null) return;
    for (String path : layerPaths) {
      Image img = loadImage(path);
      if (img != null) {
        gc.drawImage(img, x, y, width, height);
      }
    }
  }

  private void renderAudioVisualizer(double width, double height) {
    if (!isAudioVisualizerEnabled()) {
      decayVisualizer(0.86);
      return;
    }
    int activeBars = resolveAudioVisualizerBarCount();
    if (activeBars <= 0) {
      decayVisualizer(0.86);
      return;
    }
    if (audioFacade == null) {
      decayVisualizer(0.86);
      return;
    }

    float[] magnitudes = audioFacade.getBgmSpectrumMagnitudes();
    long updatedAt = audioFacade.getBgmSpectrumUpdatedAtNanos();
    long nowNs = System.nanoTime();
    boolean hasFreshData = magnitudes != null
        && magnitudes.length > 0
        && (updatedAt <= 0L || (nowNs - updatedAt) <= VISUALIZER_STALE_NS);

    if (hasFreshData) {
      mapSpectrumToTargets(magnitudes, visualizerTargets, activeBars);
      for (int i = 0; i < activeBars; i++) {
        double eased = visualizerLevels[i] * 0.62 + visualizerTargets[i] * 0.38;
        visualizerLevels[i] = clamp(eased, 0.0, 1.0);
      }
      for (int i = activeBars; i < visualizerLevels.length; i++) {
        visualizerLevels[i] = 0.0;
        visualizerTargets[i] = 0.0;
      }
    } else {
      decayVisualizer(0.9);
    }

    double maxLevel = 0.0;
    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level > maxLevel) maxLevel = level;
    }
    if (maxLevel < 0.015) return;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    // Fill the entire area above the textbox.
    double regionBottom = Math.min(height, textBox.y() - 2.0);
    double regionTop = 0.0;
    if (regionBottom <= regionTop + 8) return;

    double regionHeight = regionBottom - regionTop;
    double sidePadding = 0.0;
    double regionWidth = Math.max(1.0, width);
    double gap = 1.0;
    double barWidth = (regionWidth - gap * (activeBars - 1)) / activeBars;
    if (barWidth < 1.0) return;

    gc.save();
    gc.setGlobalAlpha(1.0);
    gc.setStroke(Color.WHITE);
    gc.setLineWidth(1.0);
    gc.strokeLine(sidePadding, regionBottom + 0.5, sidePadding + regionWidth, regionBottom + 0.5);

    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level <= 0.002) continue;
      double normalized = Math.pow(level, 0.78);
      double barHeight = Math.max(2.0, normalized * regionHeight);
      double x = sidePadding + i * (barWidth + gap);
      double y = regionBottom - barHeight;

      gc.setFill(Color.WHITE);
      gc.fillRoundRect(x, y, barWidth, barHeight, 3.0, 3.0);
    }
    gc.restore();
  }

  private void decayVisualizer(double factor) {
    for (int i = 0; i < visualizerLevels.length; i++) {
      visualizerLevels[i] *= factor;
      if (visualizerLevels[i] < 0.0001) visualizerLevels[i] = 0.0;
    }
  }

  private void mapSpectrumToTargets(float[] magnitudes, double[] out, int activeBars) {
    Arrays.fill(out, 0.0);
    if (magnitudes == null || magnitudes.length == 0 || out.length == 0 || activeBars <= 0) return;
    double bandsPerBar = magnitudes.length / (double) activeBars;

    for (int i = 0; i < activeBars; i++) {
      int start = (int) Math.floor(i * bandsPerBar);
      int end = (int) Math.ceil((i + 1) * bandsPerBar);
      if (end <= start) end = start + 1;
      start = Math.max(0, Math.min(start, magnitudes.length - 1));
      end = Math.max(start + 1, Math.min(end, magnitudes.length));

      double sum = 0.0;
      int count = 0;
      for (int j = start; j < end; j++) {
        double db = magnitudes[j];
        double normalized = (db + 60.0) / 60.0;
        normalized = clamp(normalized, 0.0, 1.0);
        normalized = Math.pow(normalized, 0.72);
        sum += normalized;
        count++;
      }
      double avg = count == 0 ? 0.0 : (sum / count);
      double freqWeight = 1.0 - (i / (double) activeBars) * 0.35;
      out[i] = clamp(avg * freqWeight, 0.0, 1.0);
    }
  }

  private void renderDialogue(DialogueLine dialogue, VnState state, double width, double height, int hoveredButtonIndex) {
    if (dialogue == null) return;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    double textBoxX = textBox.x();
    double textBoxY = textBox.y();
    double textBoxWidth = textBox.width();
    double textBoxHeight = textBox.height();

    // Draw text box background (asset if provided, otherwise default fill).
    boolean clipTextBox = hasPolygon(textBoxBoundsPolygon);
    if (clipTextBox) {
      gc.save();
      clipToLocalPolygon(textBoxBoundsPolygon, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
    }
    if (textBoxImage != null) {
      gc.drawImage(textBoxImage, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
      if (textBoxAssetOverlayOpacity > 0.001) {
        gc.setFill(withOpacity(textBoxFillColor, textBoxAssetOverlayOpacity));
        gc.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
      }
    } else {
      gc.setFill(textBoxFillColor);
      gc.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
    }
    if (clipTextBox) gc.restore();

    // Draw name box if speaker exists
      String speakerName = resolveRuntimeText(dialogue.getSpeakerName());
    if (speakerName != null && !speakerName.isEmpty()) {
      double nameBoxX = textBoxX + uiLayout.nameBoxXOffset();
      double nameBoxY = textBoxY + uiLayout.nameBoxYOffset();
      double nameBoxW;
      if (uiLayout.nameBoxAutoWidth()) {
        double textW = computeTextWidth(speakerName, nameFont);
        nameBoxW = Math.max(textW + uiLayout.nameTextXOffset() * 2, uiLayout.nameBoxWidth());
      } else {
        nameBoxW = uiLayout.nameBoxWidth();
      }
      double nameBoxH = uiLayout.nameBoxHeight();
      boolean clipNameBox = hasPolygon(nameBoxBoundsPolygon);
      if (clipNameBox) {
        gc.save();
        clipToLocalPolygon(nameBoxBoundsPolygon, nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      }
      double prevAlpha = gc.getGlobalAlpha();
      if (nameBoxRenderOpacity < 0.999) gc.setGlobalAlpha(prevAlpha * nameBoxRenderOpacity);
      if (nameBoxImage != null) {
        gc.drawImage(nameBoxImage, nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      } else {
        gc.setFill(nameBoxFillColor);
        gc.fillRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      }
      gc.setGlobalAlpha(prevAlpha);
      if (clipNameBox) gc.restore();

      gc.setFill(nameTextFillColor);
      gc.setFont(nameFont);
      double nameContentX = nameBoxX + uiLayout.nameTextXOffset();
      double nameContentW = Math.max(0, nameBoxW - uiLayout.nameTextXOffset() * 2);
      double nameTextW = computeTextWidth(speakerName, nameFont);
      gc.fillText(
          speakerName,
          resolveAlignedTextX(nameContentX, nameContentW, nameTextW, nameTextXAlign),
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
    double textWidth = Math.max(
        60,
        textBoxWidth - uiLayout.dialogueTextHorizontalPadding() - uiLayout.dialogueTextRightPadding());
    double textHeight = Math.max(
        20,
        textBoxHeight - uiLayout.dialogueTextTopPadding() - uiLayout.dialogueTextBottomPadding());
    gc.save();
    if (hasPolygon(dialogueTextBoundsPolygon)) {
      clipToLocalPolygon(dialogueTextBoundsPolygon, textX, textY, textWidth, textHeight);
    } else {
      gc.beginPath();
      gc.rect(textX, textY - dialogueFont.getSize(), textWidth, textHeight + dialogueFont.getSize());
      gc.closePath();
      gc.clip();
    }
    drawStyledText(spans, revealedLength, textX, textY, textWidth, dialogueTextXAlign);
    gc.restore();

    // Draw continue indicator if text is fully revealed
    if (revealedLength >= plainLength && state.isWaitingForInput()) {
      drawContinueIndicator(textBoxX + textBoxWidth - 30, textBoxY + textBoxHeight - 20);
    }

    renderTextBoxButtons(textBox, hoveredButtonIndex);
  }

  private void renderTextBoxButtons(TextBoxGeometry textBox, int hoveredButtonIndex) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return;
    for (int i = 0; i < textBoxButtons.size(); i++) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox);
      boolean hovered = i == hoveredButtonIndex;
      boolean enabled = button.enabled();

      Image asset = firstNonBlank(button.assetPath(), null) != null ? loadImage(button.assetPath()) : null;
      Image hoverAsset = firstNonBlank(button.hoverAssetPath(), null) != null ? loadImage(button.hoverAssetPath()) : asset;
      Image disabledAsset = firstNonBlank(button.disabledAssetPath(), null) != null ? loadImage(button.disabledAssetPath()) : asset;
      Image drawAsset = !enabled
          ? firstNonNull(disabledAsset, asset)
          : (hovered ? firstNonNull(hoverAsset, asset) : asset);
      List<BoundsPointCodec.Point> buttonPolygon = parseBoundsPoints(button.boundsPoints());
      boolean clipButton = hasPolygon(buttonPolygon);
      if (drawAsset != null) {
        if (clipButton) {
          gc.save();
          clipToLocalPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
        }
        if (!enabled) gc.setGlobalAlpha(0.55);
        gc.drawImage(drawAsset, geometry.x(), geometry.y(), geometry.width(), geometry.height());
        gc.setGlobalAlpha(1.0);
        if (clipButton) gc.restore();
      } else {
        Color fill = !enabled
            ? Color.rgb(38, 40, 48, 0.7)
            : (hovered ? Color.rgb(90, 120, 180, 0.8) : Color.rgb(32, 36, 46, 0.78));
        if (clipButton) {
          gc.save();
          clipToLocalPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
          gc.setFill(fill);
          gc.fillRect(geometry.x(), geometry.y(), geometry.width(), geometry.height());
          gc.restore();
        } else {
          gc.setFill(fill);
          gc.fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 8, 8);
        }
      }

      gc.setStroke(!enabled
          ? Color.rgb(120, 125, 136, 0.75)
          : (hovered ? Color.rgb(170, 210, 255, 0.95) : Color.rgb(120, 135, 170, 0.82)));
      gc.setLineWidth(hovered ? 2.0 : 1.2);
      if (clipButton) {
        strokeLocalPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
      } else {
        gc.strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 8, 8);
      }

      gc.setFill(!enabled ? Color.rgb(172, 176, 188, 0.75) : (hovered ? Color.rgb(245, 252, 255) : Color.rgb(225, 232, 246)));
      gc.setFont(Font.font(choiceFont.getFamily(), FontWeight.BOLD, clamp(geometry.height() * 0.42, 10, 18)));
      String label = button.label() == null || button.label().isBlank() ? button.id() : button.label();
      double textW = computeTextWidth(label, gc.getFont());
      double textX = geometry.x() + Math.max(8, (geometry.width() - textW) / 2.0);
      double textY = geometry.y() + geometry.height() * 0.64;
      gc.fillText(label, textX, textY);
    }
  }

  private record StyledGlyph(char value, Font font, Color color, TextEffect effect, int glyphIndex, double width) {}

  private record StyledLine(List<StyledGlyph> glyphs, double width) {}

  private void drawStyledText(List<TextSpan> spans, int revealedChars, double startX, double startY, double maxWidth, double xAlign) {
    gc.setFont(dialogueFont);
    double lineHeight = Math.max(22.0, dialogueFont.getSize() * 1.15);
    List<StyledLine> lines = new ArrayList<>();
    List<StyledGlyph> currentLine = new ArrayList<>();
    double currentLineWidth = 0.0;
    int charCount = 0;
    int glyphIndex = 0;

    for (TextSpan span : spans) {
      String text = span.getText();
      int spanLen = text.length();
      int visibleChars = 0;
      if (charCount < revealedChars) {
        visibleChars = Math.min(spanLen, revealedChars - charCount);
      }

      if (visibleChars > 0) {
        Color spanColor = span.hasColor() ? parseColorHex(span.getColorHex()) : dialogueTextFillColor;
        Font effectFont = dialogueFont;
        if (span.getEffect() == TextEffect.BOLD) {
          effectFont = Font.font(dialogueFont.getFamily(), FontWeight.BOLD, dialogueFont.getSize());
        } else if (span.getEffect() == TextEffect.ITALIC) {
          effectFont = Font.font(dialogueFont.getFamily(), FontWeight.NORMAL, dialogueFont.getSize());
        }

        for (int i = 0; i < visibleChars; i++) {
          char c = text.charAt(i);
          if (c == '\n') {
            lines.add(new StyledLine(List.copyOf(currentLine), currentLineWidth));
            currentLine.clear();
            currentLineWidth = 0.0;
            continue;
          }

          double charWidth = computeTextWidth(String.valueOf(c), effectFont);
          if (!currentLine.isEmpty() && currentLineWidth + charWidth > maxWidth) {
            lines.add(new StyledLine(List.copyOf(currentLine), currentLineWidth));
            currentLine.clear();
            currentLineWidth = 0.0;
          }

          currentLine.add(new StyledGlyph(c, effectFont, spanColor, span.getEffect(), glyphIndex, charWidth));
          currentLineWidth += charWidth;
          glyphIndex++;
        }
      }

      charCount += spanLen;
    }

    if (!currentLine.isEmpty() || lines.isEmpty()) {
      lines.add(new StyledLine(List.copyOf(currentLine), currentLineWidth));
    }

    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      StyledLine line = lines.get(lineIndex);
      double x = resolveAlignedTextX(startX, maxWidth, line.width(), xAlign);
      double y = startY + lineIndex * lineHeight;

      for (StyledGlyph glyph : line.glyphs()) {
        gc.setFill(glyph.color());
        gc.setFont(glyph.font());

        double offsetX = 0.0;
        double offsetY = 0.0;
        double effectPhase = (animationTime * 0.01) + glyph.glyphIndex() * 0.3;
        switch (glyph.effect()) {
          case SHAKE -> {
            offsetX = (Math.random() - 0.5) * 3;
            offsetY = (Math.random() - 0.5) * 3;
          }
          case WAVE -> offsetY = Math.sin(effectPhase) * 3;
          case BOUNCE -> offsetY = Math.abs(Math.sin(effectPhase * 2)) * -4;
          case RAINBOW -> gc.setFill(Color.hsb((effectPhase * 50) % 360, 0.8, 1.0));
          default -> {}
        }

        gc.fillText(String.valueOf(glyph.value()), x + offsetX, y + offsetY);
        x += glyph.width();
      }
    }

    gc.setFont(dialogueFont);
    gc.setFill(dialogueTextFillColor);
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

  private void applyUiStyle(VnUiStyleSpec style) {
    VnUiStyleSpec resolved = style == null ? VnUiStyleSpec.defaults() : style;
    textBoxImage = loadImage(resolved.textBoxAssetPath());
    nameBoxImage = loadImage(resolved.nameBoxAssetPath());
    textBoxFillColor = parseColor(resolved.textBoxColor(), TEXTBOX_COLOR);
    nameBoxFillColor = parseColor(resolved.nameBoxColor(), NAME_BOX_COLOR);
    nameTextFillColor = parseColor(resolved.nameTextColor(), Color.web("#FFD78A"));
    dialogueTextFillColor = parseColor(resolved.dialogueTextColor(), TEXT_COLOR);
    textBoxAssetOverlayOpacity = clamp(
        resolved.textBoxOpacity() == null ? 0.88 : resolved.textBoxOpacity(),
        0.0,
        1.0
    );
    textBoxBoundsPolygon = parseBoundsPoints(resolved.textBoxBoundsPoints());
    nameBoxBoundsPolygon = parseBoundsPoints(resolved.nameBoxBoundsPoints());
    dialogueTextBoundsPolygon = parseBoundsPoints(resolved.dialogueTextBoundsPoints());
    choiceButtonImage = loadImage(resolved.choiceButtonAssetPath());
    choiceButtonHoverImage = loadImage(firstNonBlank(
        resolved.choiceButtonHoverAssetPath(),
        resolved.choiceButtonSelectedAssetPath()));
    choiceButtonDisabledImage = loadImage(resolved.choiceButtonDisabledAssetPath());
    choiceButtonBoundsPolygon = parseBoundsPoints(resolved.choiceButtonBoundsPoints());

    choiceBgColor = parseColor(resolved.choiceBackgroundColor(), CHOICE_BG_COLOR);
    choiceHoverColor = parseColor(
        firstNonBlank(resolved.choiceHoverColor(), resolved.choiceSelectedColor()),
        CHOICE_HOVER_COLOR
    );
    choiceDisabledColor = parseColor(resolved.choiceDisabledColor(), CHOICE_DISABLED_COLOR);

    choiceTextColor = parseColor(resolved.choiceTextColor(), TEXT_COLOR);
    choiceHoverTextColor = parseColor(
        firstNonBlank(resolved.choiceHoverTextColor(), resolved.choiceSelectedTextColor()),
        choiceTextColor
    );
    choiceDisabledTextColor = parseColor(resolved.choiceDisabledTextColor(), TEXT_COLOR_DISABLED);

    choiceBorderColor = parseColor(resolved.choiceBorderColor(), TEXT_COLOR);
    choiceHoverBorderColor = parseColor(
        firstNonBlank(resolved.choiceHoverBorderColor(), resolved.choiceSelectedBorderColor()),
        choiceBorderColor
    );
    choiceDisabledBorderColor = parseColor(resolved.choiceDisabledBorderColor(), CHOICE_DISABLED_BORDER_COLOR);

    choiceBorderWidth = clamp(resolved.choiceBorderWidth(), 0.0, 12.0);
    choiceCornerRadius = clamp(resolved.choiceCornerRadius(), 0.0, 96.0);
    choiceTextBaselineOffset = clamp(resolved.choiceTextBaselineOffset(), -120.0, 120.0);
    nameTextXAlign = clamp(resolved.nameTextXAlign() == null ? 0.0 : resolved.nameTextXAlign(), 0.0, 1.0);
    dialogueTextXAlign = clamp(resolved.dialogueTextXAlign() == null ? 0.0 : resolved.dialogueTextXAlign(), 0.0, 1.0);
    choiceTextXAlign = clamp(resolved.choiceTextXAlign() == null ? 0.0 : resolved.choiceTextXAlign(), 0.0, 1.0);

    // Apply font settings from style spec (font weight from spec, with sensible defaults)
    String nameFontFamily = resolved.nameTextFontFamily() != null ? resolved.nameTextFontFamily() : DEFAULT_FONT_FAMILY;
    int nameFontSize = resolved.nameTextFontSize() != null ? resolved.nameTextFontSize() : DEFAULT_NAME_FONT_SIZE;
    FontWeight nameFontWeight = parseFontWeight(resolved.nameTextFontWeight(), FontWeight.BOLD);
    this.nameFont = Font.font(nameFontFamily, nameFontWeight, nameFontSize);

    String dialogueFontFamily = resolved.dialogueTextFontFamily() != null ? resolved.dialogueTextFontFamily() : DEFAULT_FONT_FAMILY;
    int dialogueFontSize = resolved.dialogueTextFontSize() != null ? resolved.dialogueTextFontSize() : DEFAULT_DIALOGUE_FONT_SIZE;
    FontWeight dialogueFontWeight = parseFontWeight(resolved.dialogueTextFontWeight(), FontWeight.NORMAL);
    this.dialogueFont = Font.font(dialogueFontFamily, dialogueFontWeight, dialogueFontSize);

    String choiceFontFamily = resolved.choiceFontFamily() != null ? resolved.choiceFontFamily() : DEFAULT_FONT_FAMILY;
    int choiceFontSize = resolved.choiceFontSize() != null ? resolved.choiceFontSize() : DEFAULT_CHOICE_FONT_SIZE;
    FontWeight choiceFontWeightVal = parseFontWeight(resolved.choiceFontWeight(), FontWeight.NORMAL);
    this.choiceFont = Font.font(choiceFontFamily, choiceFontWeightVal, choiceFontSize);

    // Name box opacity
    this.nameBoxRenderOpacity = clamp(
        resolved.nameBoxOpacity() == null ? 1.0 : resolved.nameBoxOpacity(),
        0.0, 1.0
    );

    // Character framing: lets projects opt into waist-up portraits.
    this.styleCharacterHeightFactor = clamp(
        resolved.characterHeightFactor() == null ? DEFAULT_CHARACTER_HEIGHT_FACTOR : resolved.characterHeightFactor(),
        0.1, 3.0
    );
    this.styleCharacterBaselineY = clamp(
        resolved.characterBaselineY() == null ? DEFAULT_CHARACTER_BASELINE_Y : resolved.characterBaselineY(),
        -0.5, 2.0
    );
    this.characterHeightFactor = styleCharacterHeightFactor;
    this.characterBaselineY = styleCharacterBaselineY;
  }

  private void applyRuntimeCharacterFramingOverrides(VnState state) {
    if (state == null) {
      characterHeightFactor = styleCharacterHeightFactor;
      characterBaselineY = styleCharacterBaselineY;
      return;
    }
    Double heightOverride = readDoubleVariable(state, VAR_CHARACTER_HEIGHT_FACTOR);
    Double baselineOverride = readDoubleVariable(state, VAR_CHARACTER_BASELINE_Y);
    characterHeightFactor = clamp(
        heightOverride == null ? styleCharacterHeightFactor : heightOverride,
        0.1,
        3.0
    );
    characterBaselineY = clamp(
        baselineOverride == null ? styleCharacterBaselineY : baselineOverride,
        -0.5,
        2.0
    );
  }

  private Double readDoubleVariable(VnState state, String key) {
    if (state == null || key == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    if (value instanceof Number n) return n.doubleValue();
    if (value instanceof String s) {
      try {
        return Double.parseDouble(s.trim());
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private boolean isAudioVisualizerEnabled() {
    if (currentState == null) return false;
    Object value = currentState.getVariables().get(VAR_AUDIO_VISUALIZER_ENABLED);
    if (value == null) return false;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.doubleValue() != 0.0;
    if (value instanceof String s) {
      String t = s.trim().toLowerCase();
      return "1".equals(t) || "true".equals(t) || "on".equals(t) || "yes".equals(t);
    }
    return false;
  }

  private int resolveAudioVisualizerBarCount() {
    Double override = readDoubleVariable(currentState, VAR_AUDIO_VISUALIZER_BARS);
    if (override == null) return VISUALIZER_BAR_COUNT;
    return (int) Math.round(clamp(override, 8.0, VISUALIZER_BAR_COUNT));
  }

  private Color parseColor(String raw, Color fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static FontWeight parseFontWeight(String raw, FontWeight def) {
    if (raw == null || raw.isBlank()) return def;
    try {
      return FontWeight.valueOf(raw.trim().toUpperCase());
    } catch (Exception ignored) {
      return def;
    }
  }

  private List<BoundsPointCodec.Point> parseBoundsPoints(String raw) {
    List<BoundsPointCodec.Point> parsed = BoundsPointCodec.parse(raw);
    return parsed.size() >= 3 ? parsed : List.of();
  }

  private boolean hasPolygon(List<BoundsPointCodec.Point> points) {
    return points != null && points.size() >= 3;
  }

  private void clipToLocalPolygon(List<BoundsPointCodec.Point> localPoints, double rectX, double rectY, double rectW, double rectH) {
    if (!hasPolygon(localPoints)) return;
    gc.beginPath();
    for (int i = 0; i < localPoints.size(); i++) {
      BoundsPointCodec.Point point = localPoints.get(i);
      double x = rectX + rectW * point.x();
      double y = rectY + rectH * point.y();
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.closePath();
    gc.clip();
  }

  private void strokeLocalPolygon(List<BoundsPointCodec.Point> localPoints, double rectX, double rectY, double rectW, double rectH) {
    if (!hasPolygon(localPoints)) return;
    gc.beginPath();
    for (int i = 0; i < localPoints.size(); i++) {
      BoundsPointCodec.Point point = localPoints.get(i);
      double x = rectX + rectW * point.x();
      double y = rectY + rectH * point.y();
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.closePath();
    gc.stroke();
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return null;
  }

  private static Image firstNonNull(Image primary, Image fallback) {
    return primary != null ? primary : fallback;
  }

  private static Color withOpacity(Color base, double opacity) {
    if (base == null) base = Color.BLACK;
    double clamped = opacity;
    if (Double.isNaN(clamped) || Double.isInfinite(clamped)) clamped = 0.0;
    if (clamped < 0.0) clamped = 0.0;
    if (clamped > 1.0) clamped = 1.0;
    return Color.color(base.getRed(), base.getGreen(), base.getBlue(), clamped);
  }

  private void renderChoices(List<Choice> choices, double width, double height, int hoverIndex) {
    if (choices == null || choices.isEmpty()) return;
    ChoiceGeometry geo = computeChoiceGeometry(choices.size(), width, height);

    for (int i = 0; i < choices.size(); i++) {
      Choice choice = choices.get(i);
      double y = geo.startY() + i * (geo.choiceHeight() + geo.choiceGap());
      boolean enabled = choice.isEnabled() && choiceConditionSatisfied(choice);
      boolean hovered = i == hoverIndex;

      Image buttonImage = !enabled
          ? firstNonNull(choiceButtonDisabledImage, choiceButtonImage)
          : (hovered ? firstNonNull(choiceButtonHoverImage, choiceButtonImage) : choiceButtonImage);
      double radius = choiceCornerRadius;
      boolean clipChoiceButton = hasPolygon(choiceButtonBoundsPolygon);
      if (buttonImage != null) {
        if (clipChoiceButton) {
          gc.save();
          clipToLocalPolygon(choiceButtonBoundsPolygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.drawImage(buttonImage, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.restore();
        } else {
          gc.drawImage(buttonImage, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
        }
      } else {
        Color bg = !enabled ? choiceDisabledColor : (hovered ? choiceHoverColor : choiceBgColor);
        if (clipChoiceButton) {
          gc.save();
          clipToLocalPolygon(choiceButtonBoundsPolygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.setFill(bg);
          gc.fillRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
          gc.restore();
        } else {
          gc.setFill(bg);
          gc.fillRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), radius, radius);
        }
      }

      // Border
      Color borderColor = !enabled ? choiceDisabledBorderColor : (hovered ? choiceHoverBorderColor : choiceBorderColor);
      gc.setStroke(borderColor);
      gc.setLineWidth(choiceBorderWidth);
      if (clipChoiceButton) {
        strokeLocalPolygon(choiceButtonBoundsPolygon, geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight());
      } else {
        gc.strokeRoundRect(geo.choiceX(), y, geo.choiceWidth(), geo.choiceHeight(), radius, radius);
      }

      // Text
      Color textColor = !enabled ? choiceDisabledTextColor : (hovered ? choiceHoverTextColor : choiceTextColor);
      gc.setFill(textColor);
      gc.setFont(choiceFont);
      String choiceText = resolveRuntimeText(choice.getText());
      double contentX = geo.choiceX() + uiLayout.choiceTextXPadding();
      double contentWidth = Math.max(0, geo.choiceWidth() - uiLayout.choiceTextXPadding() * 2);
      double textWidth = computeTextWidth(choiceText, choiceFont);
      gc.fillText(
          choiceText,
          resolveAlignedTextX(contentX, contentWidth, textWidth, choiceTextXAlign),
          y + geo.choiceHeight() / 2 + choiceTextBaselineOffset
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
    gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 32));
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
      gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 22));
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

  private double resolveAlignedTextX(double contentX, double contentWidth, double textWidth, double xAlign) {
    double clampedAlign = clamp(xAlign, 0.0, 1.0);
    double available = Math.max(0.0, contentWidth - textWidth);
    return contentX + available * clampedAlign;
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
      if (hasPolygon(choiceButtonBoundsPolygon)) {
        if (BoundsPointCodec.containsInRect(
            choiceButtonBoundsPolygon,
            geo.choiceX(),
            y,
            geo.choiceWidth(),
            geo.choiceHeight(),
            mouseX,
            mouseY
        )) return i;
      } else if (mouseX >= geo.choiceX() && mouseX <= geo.choiceX() + geo.choiceWidth()
          && mouseY >= y && mouseY <= y + geo.choiceHeight()) {
        return i;
      }
    }
    return -1;
  }

  public VnUiActionButtonSpec getHoveredTextBoxButton(VnState state, double width, double height, double mouseX, double mouseY) {
    int idx = getHoveredTextBoxButtonIndex(state, width, height, mouseX, mouseY);
    if (idx < 0 || idx >= textBoxButtons.size()) return null;
    return textBoxButtons.get(idx);
  }

  private int getHoveredTextBoxButtonIndex(VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || state.isUiHidden()) return -1;
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    VnNode currentNode = state.getCurrentNode();
    if (currentNode == null || currentNode.getType() != VnNodeType.DIALOGUE) return -1;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox);
      if (buttonContainsPoint(button, geometry, mouseX, mouseY)) return i;
    }
    return -1;
  }

  private boolean buttonContainsPoint(VnUiActionButtonSpec button, ButtonGeometry geometry, double mouseX, double mouseY) {
    if (button == null || geometry == null) return false;
    String raw = button.boundsPoints();
    if (raw != null && !raw.isBlank()) {
      List<BoundsPointCodec.Point> points = BoundsPointCodec.parse(raw);
      if (points.size() >= 3) {
        return BoundsPointCodec.containsInRect(
            points,
            geometry.x(),
            geometry.y(),
            geometry.width(),
            geometry.height(),
            mouseX,
            mouseY
        );
      }
    }
    return geometry.contains(mouseX, mouseY);
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

  private TextBoxGeometry computeTextBoxGeometry(double width, double height) {
    double textBoxX = clamp(width * uiLayout.textBoxX(), 0, width);
    double textBoxY = clamp(height * uiLayout.textBoxY(), 0, height);
    double maxBoxWidth = Math.max(1, width - textBoxX);
    double maxBoxHeight = Math.max(1, height - textBoxY);
    double textBoxWidth = clamp(width * uiLayout.textBoxWidth(), 1, maxBoxWidth);
    double textBoxHeight = clamp(height * uiLayout.textBoxHeight(), 1, maxBoxHeight);
    return new TextBoxGeometry(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
  }

  private ButtonGeometry computeButtonGeometry(VnUiActionButtonSpec button, TextBoxGeometry textBox) {
    double x = textBox.x() + textBox.width() * button.x();
    double y = textBox.y() + textBox.height() * button.y();
    double width = Math.max(8, textBox.width() * button.width());
    double height = Math.max(8, textBox.height() * button.height());
    return new ButtonGeometry(x, y, width, height);
  }

  private record ChoiceGeometry(double choiceX, double startY, double choiceWidth, double choiceHeight, double choiceGap) {}
  private record TextBoxGeometry(double x, double y, double width, double height) {}
  private record ButtonGeometry(double x, double y, double width, double height) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + width && py >= y && py <= y + height;
    }
  }

  private Image loadImage(String path) {
    if (path == null) return null;
    
    return imageCache.computeIfAbsent(path, p -> {
      try {
        // Prefer configured asset manager (runtime/editor project overlay support).
        var assetUrl = new AssetCatalog().url(AssetType.IMAGE, p);
        if (assetUrl != null) {
          return new Image(assetUrl.toExternalForm());
        }

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
    gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 14));
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
