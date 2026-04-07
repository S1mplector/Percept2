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
import com.jvn.core.vn.BubbleAnchor;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialoguePresentationMode;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnAudioVisualizerConfig;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.text.TextEffect;
import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnOverlayScreenSpec;
import com.jvn.fx.ui.ProjectFontResolver;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.canvas.Canvas;
import javafx.scene.SnapshotParameters;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Renders visual novel elements using JavaFX Canvas
 */
public class VnRenderer {
  private final GraphicsContext gc;
  private final Map<String, Image> imageCache = new HashMap<>();
  private final Map<String, Image> stageBackgroundCache = new HashMap<>();
  private final Map<String, Image> stageCharacterCache = new HashMap<>();
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
  private static final int VISUALIZER_BAR_COUNT = VnAudioVisualizerConfig.MAX_BARS;
  private static final String VAR_CHARACTER_HEIGHT_FACTOR = "ui.characterHeightFactor";
  private static final String VAR_CHARACTER_BASELINE_Y = "ui.characterBaselineY";

  private Image choiceButtonImage;
  private Image choiceButtonHoverImage;
  private Image choiceButtonDisabledImage;
  private Image textBoxImage;
  private Image narrationTextBoxImage;
  private Image nameBoxImage;
  private Image nvlPanelImage;
  private Image bubbleImage;
  private Color textBoxFillColor = TEXTBOX_COLOR;
  private Color nameBoxFillColor = NAME_BOX_COLOR;
  private Color nameTextFillColor = Color.web("#FFD78A");
  private Color dialogueTextFillColor = TEXT_COLOR;
  private Color nvlPanelFillColor = Color.web("#08111acc");
  private Color nvlSpeakerTextFillColor = Color.web("#F7D89A");
  private Color nvlTextFillColor = TEXT_COLOR;
  private Color bubbleFillColor = Color.web("#152238ee");
  private Color bubbleBorderFillColor = Color.web("#A9BCD9");
  private Color bubbleSpeakerTextFillColor = Color.web("#FFD78A");
  private Color bubbleTextFillColor = Color.web("#F1F5FF");
  private double textBoxAssetOverlayOpacity = 0.28;
  private double nameBoxRenderOpacity = 1.0;
  private double nvlPanelOpacity = 0.84;
  private double bubbleOpacity = 0.96;
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
  private double bubbleCornerRadius = 20.0;
  private double bubbleBorderWidth = 2.0;
  private double nameTextXAlign = 0.0;
  private double dialogueTextXAlign = 0.0;
  private double choiceTextXAlign = 0.0;
  private List<BoundsPointCodec.Point> textBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> nameBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> dialogueTextBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> choiceButtonBoundsPolygon = List.of();
  private final double[] visualizerLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerTargets = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerLevelVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerPeakLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerPeakVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerWidthMultipliers = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerWidthVelocities = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerGlowLevels = new double[VISUALIZER_BAR_COUNT];
  private final double[] visualizerBassHistory = new double[12];
  private int visualizerBassHistoryIndex = 0;
  private long visualizerLastBeatAtNanos = 0L;
  private double visualizerBeatFlashIntensity = 0.0;
  private double visualizerHue = 182.0;

  public VnRenderer(GraphicsContext gc) {
    this.gc = gc;
    this.nameFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.BOLD, DEFAULT_NAME_FONT_SIZE);
    this.dialogueFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_DIALOGUE_FONT_SIZE);
    this.choiceFont = Font.font(DEFAULT_FONT_FAMILY, FontWeight.NORMAL, DEFAULT_CHOICE_FONT_SIZE);
    Arrays.fill(visualizerWidthMultipliers, 1.0);
    reloadUiLayout();
  }

  // Optional base directory used to resolve asset paths from filesystem (editor preview)
  private File projectRoot;
  public void setProjectRoot(File root) {
    this.projectRoot = root;
    stageBackgroundCache.clear();
    stageCharacterCache.clear();
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
    VnStagePreset activeStage = resolveActiveStagePreset(state, scenario);
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

    boolean handledTransitionBackground = activeStage != null && activeStage.getBackgroundTag() != null && !activeStage.getBackgroundTag().isBlank();
    var transition = handledTransitionBackground ? null : state.getActiveTransition();
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
      VnBackground bg = state.getCurrentBackgroundId() != null ? scenario.getBackground(state.getCurrentBackgroundId()) : null;
      if (bg != null || activeStage != null) {
        renderBackground(bg, activeStage, width, height);
      }
    } else if (activeStage != null) {
      renderBackground(null, activeStage, width, height);
    }

    // Apply transition effect if active
    if (state.getActiveTransition() != null) {
      renderTransitionOverlay(state, width, height);
    }

    List<Map.Entry<CharacterPosition, VnState.CharacterSlot>> orderedCharacters = orderedCharacterEntries(state);
    AudioVisualizerSettings visualizerSettings = resolveAudioVisualizerSettings();
    int visualizerSplit = resolveVisualizerCharacterSplit(orderedCharacters, visualizerSettings.zIndex());

    renderCharacters(orderedCharacters.subList(0, visualizerSplit), state, scenario, activeStage, width, height);
    renderAudioVisualizer(width, height, visualizerSettings);
    renderCharacters(orderedCharacters.subList(visualizerSplit, orderedCharacters.size()), state, scenario, activeStage, width, height);
    renderStageLightOverlays(activeStage, width, height, VnStagePreset.LightLayer.FOREGROUND);

    // Render current node content (unless UI is hidden)
    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden()) {
      switch (currentNode.getType()) {
        case DIALOGUE:
          renderDialogue(currentNode.getDialogue(), state, width, height, -1);
          break;
        case CHOICE:
          if (state.getDialoguePresentationMode() == DialoguePresentationMode.NVL) {
            renderNvlHistory(state, width, height);
          }
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

    renderOverlayScreens(state, width, height, null);

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
        if (state.getDialoguePresentationMode() == DialoguePresentationMode.NVL) {
          renderNvlHistory(state, width, height);
        }
        renderChoices(currentNode.getChoices(), width, height, hoverIndex);
      } else if (currentNode.getType() == VnNodeType.DIALOGUE) {
        int hoverButton = getHoveredTextBoxButtonIndex(state, width, height, mouseX, mouseY);
        renderDialogue(currentNode.getDialogue(), state, width, height, hoverButton);
      }
    }
    renderOverlayScreens(state, width, height, getHoveredOverlayButton(state, width, height, mouseX, mouseY));
  }

  private int positionOrdinal(CharacterPosition position) {
    if (position == null) return 0;
    return position.getOrdinal();
  }

  private void renderBackground(VnBackground background, VnStagePreset stage, double width, double height) {
    String backgroundPath = resolveBackgroundPath(background, stage);
    if (backgroundPath == null || backgroundPath.isBlank()) return;
    Image img = loadImage(backgroundPath);
    com.jvn.core.scene2d.Entity2D proxy = timelineAccessor != null
        && background != null
        && (stage == null || stage.getBackgroundTag() == null || stage.getBackgroundTag().isBlank())
        ? timelineAccessor.getProxy(background.getId())
        : null;
    if (img != null) {
      if (stage != null && proxy == null) {
        drawStageBackgroundImage(backgroundPath, img, stage, width, height);
      } else {
        drawBackgroundImage(img, proxy, width, height);
        if (stage != null) {
          applyStageBackgroundFallbackOverlay(stage, width, height);
          renderStageLightOverlays(stage, width, height, VnStagePreset.LightLayer.BACKGROUND);
        }
      }
    } else {
      // Placeholder background
      gc.setFill(Color.DARKSLATEGRAY);
      gc.fillRect(0, 0, width, height);
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 24));
      gc.fillText("No Background Image", 20, 40);
    }
  }

  private String resolveBackgroundPath(VnBackground background, VnStagePreset stage) {
    if (stage != null && stage.getBackgroundTag() != null && !stage.getBackgroundTag().isBlank()) {
      return stage.getBackgroundTag();
    }
    return background == null ? null : background.getImagePath();
  }

  private void drawStageBackgroundImage(String backgroundPath, Image img, VnStagePreset stage, double width, double height) {
    String key = backgroundPath
        + "|stage:" + (stage == null ? "none" : stage.getCacheToken())
        + "|size:" + Math.round(width) + "x" + Math.round(height);
    Image lit = stageBackgroundCache.computeIfAbsent(key, unused ->
        VnStageLightingSupport.buildLitBackground(img, stage, width, height));
    gc.drawImage(lit, 0, 0, width, height);
  }

  private void applyStageBackgroundFallbackOverlay(VnStagePreset stage, double width, double height) {
    if (stage == null) return;
    VnStagePreset.BackgroundGrade grade = stage.getBackgroundGrade();
    if (grade == null) return;
    Color tint = VnStageLightingSupport.parseColor(grade.tintColor(), Color.WHITE);
    double tintStrength = VnStageLightingSupport.clamp(grade.tintStrength(), 0.0, 1.0);
    if (tintStrength > 1e-6) {
      gc.setFill(Color.color(tint.getRed(), tint.getGreen(), tint.getBlue(), tintStrength * 0.14));
      gc.fillRect(0, 0, width, height);
    }
    Color overlay = VnStageLightingSupport.parseColor(grade.overlayColor(), Color.BLACK);
    double overlayOpacity = VnStageLightingSupport.clamp(grade.overlayOpacity(), 0.0, 1.0);
    if (overlayOpacity > 1e-6) {
      gc.setFill(Color.color(overlay.getRed(), overlay.getGreen(), overlay.getBlue(), overlayOpacity * 0.20));
      gc.fillRect(0, 0, width, height);
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

  private List<Map.Entry<CharacterPosition, VnState.CharacterSlot>> orderedCharacterEntries(VnState state) {
    Map<CharacterPosition, VnState.CharacterSlot> characters = state.getVisibleCharacters();

    java.util.List<Map.Entry<CharacterPosition, VnState.CharacterSlot>> ordered = new java.util.ArrayList<>(characters.entrySet());
    ordered.sort(
        java.util.Comparator
            .comparingInt((Map.Entry<CharacterPosition, VnState.CharacterSlot> e) ->
                e.getValue() != null ? e.getValue().getLayerOrder() : 0)
            .thenComparingInt(e -> positionOrdinal(e.getKey()))
    );
    return ordered;
  }

  private int resolveVisualizerCharacterSplit(List<Map.Entry<CharacterPosition, VnState.CharacterSlot>> orderedCharacters, int visualizerZ) {
    if (orderedCharacters == null || orderedCharacters.isEmpty()) return 0;
    for (int i = 0; i < orderedCharacters.size(); i++) {
      Map.Entry<CharacterPosition, VnState.CharacterSlot> entry = orderedCharacters.get(i);
      VnState.CharacterSlot slot = entry.getValue();
      int layerOrder = slot != null ? slot.getLayerOrder() : 0;
      if (layerOrder >= visualizerZ) return i;
    }
    return orderedCharacters.size();
  }

  private void renderCharacters(
      List<Map.Entry<CharacterPosition, VnState.CharacterSlot>> ordered,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double width,
      double height) {
    if (ordered == null || ordered.isEmpty()) return;
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
          renderCharacterSprite(imagePath, position, width, height, offsetX, offsetY, slot.getCharacterId(), stage);
          gc.restore();
        }
      }
    }
  }

  private void renderCharacterSprite(String imagePath, CharacterPosition position, double width, double height, double offsetX, double offsetY, String characterId, VnStagePreset stage) {
    List<String> layerPaths = parseLayerPaths(imagePath);
    Image reference = loadSpriteSourceImage(imagePath, layerPaths);

    // If a timeline proxy drives this character, use its absolute position
    if (timelineAccessor != null && characterId != null) {
      com.jvn.core.scene2d.Entity2D proxy = timelineAccessor.getProxy(characterId);
      if (proxy != null && (proxy.getX() != 0 || proxy.getY() != 0)) {
        double spriteHeight = height * characterHeightFactor;
        double spriteWidth = reference != null ? reference.getWidth() * (spriteHeight / reference.getHeight()) : spriteHeight * 0.5;
        double px = proxy.getX();
        double py = proxy.getY();
        if (reference != null) {
          drawCharacterImage(reference, imagePath, px, py, spriteWidth, spriteHeight, width, height, stage);
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
    drawCharacterImage(reference, imagePath, x + offsetX, y + offsetY, spriteWidth, spriteHeight, width, height, stage);
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

  private Image loadSpriteSourceImage(String imagePathSpec, List<String> layerPaths) {
    if (imagePathSpec == null || imagePathSpec.isBlank()) return firstAvailableImage(layerPaths);
    if (layerPaths == null || layerPaths.size() <= 1) return firstAvailableImage(layerPaths);
    String cacheKey = "__composite_sprite__:" + imagePathSpec;
    Image cached = imageCache.get(cacheKey);
    if (cached != null) return cached;
    List<Image> layers = new ArrayList<>();
    int width = 1;
    int height = 1;
    for (String path : layerPaths) {
      Image layer = loadImage(path);
      if (layer == null) continue;
      layers.add(layer);
      width = Math.max(width, (int) Math.round(layer.getWidth()));
      height = Math.max(height, (int) Math.round(layer.getHeight()));
    }
    if (layers.isEmpty()) return null;
    Canvas canvas = new Canvas(width, height);
    GraphicsContext spriteGc = canvas.getGraphicsContext2D();
    for (Image layer : layers) {
      spriteGc.drawImage(layer, 0, 0);
    }
    SnapshotParameters snapshotParameters = new SnapshotParameters();
    snapshotParameters.setFill(Color.TRANSPARENT);
    WritableImage out = new WritableImage(width, height);
    canvas.snapshot(snapshotParameters, out);
    imageCache.put(cacheKey, out);
    return out;
  }

  private void drawCharacterImage(Image source,
                                  String spriteTag,
                                  double x,
                                  double y,
                                  double drawWidth,
                                  double drawHeight,
                                  double canvasWidth,
                                  double canvasHeight,
                                  VnStagePreset stage) {
    if (source == null) return;
    if (stage == null || stage.getLights().isEmpty()) {
      gc.drawImage(source, x, y, drawWidth, drawHeight);
      return;
    }
    String key = spriteTag
        + "|stage:" + stage.getCacheToken()
        + "|pos:" + Math.round(x) + "," + Math.round(y)
        + "|size:" + Math.round(drawWidth) + "x" + Math.round(drawHeight)
        + "|canvas:" + Math.round(canvasWidth) + "x" + Math.round(canvasHeight);
    Image lit = stageCharacterCache.computeIfAbsent(key, unused ->
        VnStageLightingSupport.buildLitCharacter(source, spriteTag, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight, stage));
    gc.drawImage(lit, x, y, drawWidth, drawHeight);
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

  private VnStagePreset resolveActiveStagePreset(VnState state, VnScenario scenario) {
    if (state == null || scenario == null) return null;
    String stageId = state.getActiveStagePresetId();
    if (stageId == null || stageId.isBlank()) return null;
    return scenario.getStagePreset(stageId);
  }

  private void renderStageLightOverlays(VnStagePreset stage, double canvasWidth, double canvasHeight, VnStagePreset.LightLayer layer) {
    if (stage == null || layer == null || stage.getLights().isEmpty()) return;
    boolean hasSolo = stage.hasSoloLights();
    double minDimension = Math.max(1.0, Math.min(canvasWidth, canvasHeight));
    for (VnStagePreset.Light light : stage.getLights()) {
      if (light == null || light.layer() != layer || light.muted() || (hasSolo && !light.solo())) continue;
      Color color = VnStageLightingSupport.parseColor(light.color(), Color.web("#ffd7a8"));
      double alpha = layer == VnStagePreset.LightLayer.FOREGROUND
          ? VnStageLightingSupport.foregroundLightAlpha(light)
          : VnStageLightingSupport.backgroundLightAlpha(light);
      if (alpha <= 1e-6) continue;
      double targetX = light.sceneX() * canvasWidth;
      double targetY = light.sceneY() * canvasHeight;
      double sourceX = light.sourceX() * canvasWidth;
      double sourceY = light.sourceY() * canvasHeight;
      double radius = Math.max(10.0, light.radius() * minDimension);

      switch (light.type()) {
        case POLYGON -> drawPolygonLightOverlay(light, color, alpha, canvasWidth, canvasHeight);
        case CONE -> drawConeLightOverlay(sourceX, sourceY, targetX, targetY, radius, color, alpha);
        case STRIP, WINDOW -> drawStripLightOverlay(sourceX, sourceY, targetX, targetY, radius * 0.42, color, alpha);
        default -> {
          gc.setFill(new javafx.scene.paint.RadialGradient(
              0, 0, targetX, targetY, radius, false, CycleMethod.NO_CYCLE,
              new Stop(0.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha)),
              new Stop(0.42, Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha * 0.55)),
              new Stop(1.0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.0))
          ));
          gc.fillOval(targetX - radius, targetY - radius, radius * 2.0, radius * 2.0);
        }
      }
    }
  }

  private void drawPolygonLightOverlay(VnStagePreset.Light light, Color color, double alpha, double canvasWidth, double canvasHeight) {
    List<VnStagePreset.Point> polygon = light.polygon();
    if (polygon == null || polygon.size() < 3) return;
    double[] xs = new double[polygon.size()];
    double[] ys = new double[polygon.size()];
    for (int i = 0; i < polygon.size(); i++) {
      xs[i] = polygon.get(i).x() * canvasWidth;
      ys[i] = polygon.get(i).y() * canvasHeight;
    }
    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    gc.fillPolygon(xs, ys, xs.length);
  }

  private void drawConeLightOverlay(double sourceX, double sourceY, double targetX, double targetY, double radius, Color color, double alpha) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.max(1.0, Math.hypot(dx, dy));
    double nx = dx / length;
    double ny = dy / length;
    double px = -ny;
    double py = nx;
    double endWidth = Math.max(18.0, radius * 0.48);
    double sourceWidth = Math.max(6.0, endWidth * 0.18);
    double[] xs = {
        sourceX + px * sourceWidth,
        sourceX - px * sourceWidth,
        targetX - px * endWidth,
        targetX + px * endWidth
    };
    double[] ys = {
        sourceY + py * sourceWidth,
        sourceY - py * sourceWidth,
        targetY - py * endWidth,
        targetY + py * endWidth
    };
    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    gc.fillPolygon(xs, ys, 4);
  }

  private void drawStripLightOverlay(double sourceX, double sourceY, double targetX, double targetY, double halfWidth, Color color, double alpha) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.max(1.0, Math.hypot(dx, dy));
    double px = -dy / length;
    double py = dx / length;
    double[] xs = {
        sourceX + px * halfWidth,
        sourceX - px * halfWidth,
        targetX - px * halfWidth,
        targetX + px * halfWidth
    };
    double[] ys = {
        sourceY + py * halfWidth,
        sourceY - py * halfWidth,
        targetY - py * halfWidth,
        targetY + py * halfWidth
    };
    gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
    gc.fillPolygon(xs, ys, 4);
  }

  private void renderAudioVisualizer(double width, double height, AudioVisualizerSettings settings) {
    if (!settings.enabled()) {
      decayVisualizer(0.86, true);
      return;
    }
    int activeBars = settings.bars();
    if (activeBars <= 0) {
      decayVisualizer(0.86, true);
      return;
    }
    if (audioFacade == null) {
      decayVisualizer(0.86, true);
      return;
    }

    float[] magnitudes = audioFacade.getBgmSpectrumMagnitudes();
    long updatedAt = audioFacade.getBgmSpectrumUpdatedAtNanos();
    long nowNs = System.nanoTime();
    boolean hasFreshData = magnitudes != null
        && magnitudes.length > 0
        && (updatedAt <= 0L || (nowNs - updatedAt) <= VnAudioVisualizerConfig.STALE_NS);

    if (hasFreshData) {
      mapSpectrumToTargets(magnitudes, visualizerTargets, activeBars);
      updateAudioVisualizerState(activeBars);
    } else {
      decayVisualizer(0.9, false);
      clearInactiveVisualizerState(activeBars);
    }

    double maxLevel = 0.0;
    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level > maxLevel) maxLevel = level;
    }
    if (maxLevel < 0.015) return;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    double regionBottom = Math.min(height, textBox.y() - 2.0);
    if (regionBottom <= 8.0) return;
    double regionHeight = Math.max(24.0, regionBottom * settings.heightFactor());
    double regionTop = Math.max(0.0, regionBottom - regionHeight);
    double sidePadding = Math.max(0.0, width * 0.018);
    double regionWidth = Math.max(1.0, width - sidePadding * 2.0);
    if (regionWidth <= 8.0) return;

    AudioVisualizerPalette palette = resolveAudioVisualizerPalette(settings, maxLevel);

    gc.save();
    gc.setGlobalAlpha(1.0);
    drawAudioVisualizerBackdrop(settings, palette, sidePadding, regionTop, regionWidth, regionHeight, regionBottom);
    drawAudioVisualizerBars(settings, palette, activeBars, sidePadding, regionWidth, regionTop, regionBottom, regionHeight);
    gc.restore();
  }

  private void updateAudioVisualizerState(int activeBars) {
    boolean beat = detectVisualizerBeat(activeBars);
    visualizerBeatFlashIntensity = beat ? 1.0 : visualizerBeatFlashIntensity * 0.90;

    for (int i = 0; i < activeBars; i++) {
      double target = visualizerTargets[i];
      double diff = target - visualizerLevels[i];

      visualizerLevelVelocities[i] = (visualizerLevelVelocities[i] + diff * 0.28) * 0.84;
      visualizerLevels[i] = clamp(visualizerLevels[i] + visualizerLevelVelocities[i], 0.0, 1.0);
      if (Math.abs(diff) < 0.015) {
        visualizerLevels[i] = clamp(visualizerLevels[i] * 0.82 + target * 0.18, 0.0, 1.0);
      }

      if (visualizerLevels[i] > visualizerPeakLevels[i]) {
        visualizerPeakLevels[i] = visualizerLevels[i];
        visualizerPeakVelocities[i] = 0.0;
        visualizerGlowLevels[i] = Math.max(visualizerGlowLevels[i], 0.18 + visualizerLevels[i] * 0.82);
      } else {
        visualizerPeakVelocities[i] += 0.012 + (1.0 - visualizerLevels[i]) * 0.010;
        visualizerPeakLevels[i] = Math.max(visualizerLevels[i], visualizerPeakLevels[i] - visualizerPeakVelocities[i] * 0.045);
      }

      double targetWidth = 0.70 + Math.pow(visualizerLevels[i], 0.72) * 0.62;
      visualizerWidthVelocities[i] = (visualizerWidthVelocities[i]
          + (targetWidth - visualizerWidthMultipliers[i]) * 0.22) * 0.86;
      visualizerWidthMultipliers[i] = clamp(visualizerWidthMultipliers[i] + visualizerWidthVelocities[i], 0.62, 1.42);
      visualizerGlowLevels[i] *= 0.91;

      if (beat) {
        visualizerGlowLevels[i] = Math.max(visualizerGlowLevels[i], 0.28 + visualizerLevels[i] * 0.45);
        visualizerWidthMultipliers[i] = clamp(visualizerWidthMultipliers[i] + 0.06, 0.62, 1.42);
      }
    }

    clearInactiveVisualizerState(activeBars);
  }

  private boolean detectVisualizerBeat(int activeBars) {
    int bassBars = Math.min(6, activeBars);
    if (bassBars <= 0) return false;

    double bassEnergy = 0.0;
    for (int i = 0; i < bassBars; i++) {
      bassEnergy += visualizerTargets[i];
    }
    bassEnergy /= bassBars;

    double average = 0.0;
    for (double value : visualizerBassHistory) {
      average += value;
    }
    average /= visualizerBassHistory.length;

    visualizerBassHistory[visualizerBassHistoryIndex] = bassEnergy;
    visualizerBassHistoryIndex = (visualizerBassHistoryIndex + 1) % visualizerBassHistory.length;

    long nowNs = System.nanoTime();
    double threshold = average * 1.35 + 0.06;
    if (bassEnergy > threshold && (nowNs - visualizerLastBeatAtNanos) > 180_000_000L) {
      visualizerLastBeatAtNanos = nowNs;
      return true;
    }
    return false;
  }

  private AudioVisualizerPalette resolveAudioVisualizerPalette(AudioVisualizerSettings settings, double maxLevel) {
    boolean cycleColors = VnAudioVisualizerConfig.isAutoToken(settings.colorToken());
    if (cycleColors) {
      visualizerHue += 0.55 + maxLevel * 0.45 + visualizerBeatFlashIntensity * 0.30;
      while (visualizerHue >= 360.0) visualizerHue -= 360.0;
    }

    Color base = cycleColors
        ? Color.hsb(visualizerHue, 0.76, 1.0)
        : parseColor(settings.colorToken(), Color.web("#7DE2FF"));
    Color accent = VnAudioVisualizerConfig.isAutoToken(settings.accentToken())
        ? base.interpolate(Color.WHITE, 0.36)
        : parseColor(settings.accentToken(), base.interpolate(Color.WHITE, 0.36));
    return new AudioVisualizerPalette(base, accent, base.darker().darker());
  }

  private void drawAudioVisualizerBackdrop(
      AudioVisualizerSettings settings,
      AudioVisualizerPalette palette,
      double x,
      double regionTop,
      double regionWidth,
      double regionHeight,
      double regionBottom) {
    gc.setFill(new LinearGradient(
        0, regionTop, 0, regionBottom,
        false, CycleMethod.NO_CYCLE,
        new Stop(0.0, palette.base().deriveColor(0, 1.0, 1.12, settings.alpha() * 0.12)),
        new Stop(0.42, palette.base().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.03)),
        new Stop(1.0, Color.TRANSPARENT)));
    gc.fillRect(x, regionTop, regionWidth, regionHeight);

    if (visualizerBeatFlashIntensity > 0.02 && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      gc.setFill(palette.accent().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.08 * visualizerBeatFlashIntensity));
      gc.fillRect(x, regionTop, regionWidth, regionHeight);
    }

    gc.setStroke(palette.accent().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.28));
    gc.setLineWidth(1.0);
    gc.strokeLine(x, regionBottom + 0.5, x + regionWidth, regionBottom + 0.5);
  }

  private void drawAudioVisualizerBars(
      AudioVisualizerSettings settings,
      AudioVisualizerPalette palette,
      int activeBars,
      double sidePadding,
      double regionWidth,
      double regionTop,
      double regionBottom,
      double regionHeight) {
    double bandWidth = regionWidth / activeBars;
    double baseBarWidth = bandWidth * (VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style()) ? 0.76 : 0.68);
    boolean traceStarted = false;

    if (VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      gc.setStroke(palette.accent().deriveColor(0, 1.0, 1.0, settings.alpha() * 0.52));
      gc.setLineWidth(1.8);
      gc.beginPath();
    }

    for (int i = 0; i < activeBars; i++) {
      double level = visualizerLevels[i];
      if (level <= 0.002) continue;

      double normalized = Math.pow(level, VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style()) ? 0.68 : 0.78);
      double barHeight = Math.max(2.0, normalized * regionHeight);
      double widthMultiplier = VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style()) ? visualizerWidthMultipliers[i] : 1.0;
      double actualWidth = clamp(baseBarWidth * widthMultiplier, 1.0, Math.max(1.0, bandWidth - 0.6));
      double barX = sidePadding + i * bandWidth + (bandWidth - actualWidth) * 0.5;
      double barY = regionBottom - barHeight;

      Color barBase = palette.base().interpolate(palette.accent(), (i / (double) Math.max(1, activeBars - 1)) * 0.24);
      Color barTop = barBase.interpolate(palette.accent(), 0.46).interpolate(Color.WHITE, Math.min(0.28, level * 0.24));
      Color barBottom = palette.shadow().interpolate(barBase, 0.30);

      if (settings.glow() && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
        double glowPad = 2.0 + visualizerGlowLevels[i] * 5.0;
        gc.setFill(barBase.deriveColor(0, 1.0, 1.0, settings.alpha() * (0.08 + visualizerGlowLevels[i] * 0.12)));
        gc.fillRoundRect(
            barX - glowPad * 0.5,
            Math.max(regionTop, barY - glowPad),
            actualWidth + glowPad,
            Math.min(regionHeight, barHeight + glowPad * 1.5),
            actualWidth + glowPad,
            actualWidth + glowPad);
      }

      gc.setFill(new LinearGradient(
          0, barY, 0, regionBottom,
          false, CycleMethod.NO_CYCLE,
          new Stop(0.0, barTop.deriveColor(0, 1.0, 1.0, settings.alpha())),
          new Stop(0.55, barBase.deriveColor(0, 1.0, 1.0, settings.alpha() * 0.96)),
          new Stop(1.0, barBottom.deriveColor(0, 1.0, 1.0, settings.alpha() * 0.92))));
      gc.fillRoundRect(barX, barY, actualWidth, barHeight, 5.0, 5.0);

      if (VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
        double peakLevel = Math.max(level, visualizerPeakLevels[i]);
        if (peakLevel > level + 0.015) {
          double peakY = regionBottom - Math.max(2.0, Math.pow(peakLevel, 0.70) * regionHeight);
          gc.setFill(palette.accent().interpolate(Color.WHITE, 0.20).deriveColor(0, 1.0, 1.0, settings.alpha() * 0.90));
          gc.fillRoundRect(barX, peakY, actualWidth, 3.0, 3.0, 3.0);
        }

        double traceX = barX + actualWidth * 0.5;
        double traceY = Math.max(regionTop, barY - Math.min(14.0, 3.0 + visualizerGlowLevels[i] * 7.0));
        if (!traceStarted) {
          gc.moveTo(traceX, traceY);
          traceStarted = true;
        } else {
          gc.lineTo(traceX, traceY);
        }
      }
    }

    if (traceStarted && VnAudioVisualizerConfig.STYLE_DYNAMIC.equals(settings.style())) {
      gc.stroke();
    }
  }

  private void decayVisualizer(double factor, boolean hard) {
    for (int i = 0; i < visualizerLevels.length; i++) {
      visualizerLevels[i] *= factor;
      if (visualizerLevels[i] < 0.0001) visualizerLevels[i] = 0.0;
      visualizerLevelVelocities[i] *= hard ? 0.68 : 0.82;
      visualizerPeakVelocities[i] += hard ? 0.006 : 0.010;
      visualizerPeakLevels[i] = Math.max(visualizerLevels[i], visualizerPeakLevels[i] - visualizerPeakVelocities[i] * (hard ? 0.065 : 0.045));
      if (visualizerPeakLevels[i] < 0.0001) visualizerPeakLevels[i] = 0.0;
      visualizerWidthVelocities[i] *= hard ? 0.72 : 0.82;
      visualizerWidthMultipliers[i] = clamp(1.0 + (visualizerWidthMultipliers[i] - 1.0) * factor, 0.62, 1.42);
      if (Math.abs(visualizerWidthMultipliers[i] - 1.0) < 0.002) visualizerWidthMultipliers[i] = 1.0;
      visualizerGlowLevels[i] *= hard ? 0.80 : 0.88;
    }
    visualizerBeatFlashIntensity *= hard ? 0.82 : 0.90;
  }

  private void clearInactiveVisualizerState(int activeBars) {
    for (int i = activeBars; i < visualizerLevels.length; i++) {
      visualizerLevels[i] = 0.0;
      visualizerTargets[i] = 0.0;
      visualizerLevelVelocities[i] = 0.0;
      visualizerPeakLevels[i] = 0.0;
      visualizerPeakVelocities[i] = 0.0;
      visualizerWidthMultipliers[i] = 1.0;
      visualizerWidthVelocities[i] = 0.0;
      visualizerGlowLevels[i] = 0.0;
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

  private record AudioVisualizerSettings(
      boolean enabled,
      int bars,
      String style,
      String colorToken,
      String accentToken,
      double alpha,
      boolean glow,
      double heightFactor,
      int zIndex) {}

  private record AudioVisualizerPalette(Color base, Color accent, Color shadow) {}

  private record DialogueRenderEntry(String speaker, String text, int revealedChars) {}

  private record BubbleGeometry(
      double x,
      double y,
      double width,
      double height,
      double anchorX,
      double anchorY,
      double tailSize,
      boolean tailOnTop
  ) {}

  private void renderDialogue(DialogueLine dialogue, VnState state, double width, double height, int hoveredButtonIndex) {
    if (dialogue == null) return;
    DialoguePresentationMode mode = state == null ? DialoguePresentationMode.STANDARD : state.getDialoguePresentationMode();
    switch (mode) {
      case NVL -> renderNvlDialogue(dialogue, state, width, height);
      case BUBBLE -> renderBubbleDialogue(dialogue, state, width, height);
      default -> renderStandardDialogue(dialogue, state, width, height, hoveredButtonIndex);
    }
  }

  private void renderStandardDialogue(DialogueLine dialogue, VnState state, double width, double height, int hoveredButtonIndex) {
    if (dialogue == null) return;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    double textBoxX = textBox.x();
    double textBoxY = textBox.y();
    double textBoxWidth = textBox.width();
    double textBoxHeight = textBox.height();

    // Draw text box background (asset if provided, otherwise default fill).
    String speakerName = resolveRuntimeText(dialogue.getSpeakerName());
    boolean hasSpeaker = speakerName != null && !speakerName.isEmpty();
    Image activeTextBoxImage = hasSpeaker || narrationTextBoxImage == null ? textBoxImage : narrationTextBoxImage;
    boolean clipTextBox = hasPolygon(textBoxBoundsPolygon);
    if (clipTextBox) {
      gc.save();
      clipToLocalPolygon(textBoxBoundsPolygon, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
    }
    if (activeTextBoxImage != null) {
      gc.drawImage(activeTextBoxImage, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
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
    if (hasSpeaker) {
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
      double nameTextBaselineY = uiLayout.nameTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              nameBoxY,
              nameBoxH,
              uiLayout.nameTextTopPadding(),
              uiLayout.nameTextBottomPadding(),
              nameFont,
              uiLayout.nameTextYAlign())
          : nameBoxY + uiLayout.nameTextBaselineOffset();
      gc.fillText(
          speakerName,
          resolveAlignedTextX(nameContentX, nameContentW, nameTextW, nameTextXAlign),
          nameTextBaselineY
      );
    }

    // Parse and render dialogue text with effects
    String fullText = resolveRuntimeText(dialogue.getText());
    List<TextSpan> spans = TextParser.parse(fullText);
    int plainLength = TextParser.plainLength(fullText);
    int revealedLength = Math.min(state.getTextRevealProgress(), plainLength);

    double textX = textBoxX + uiLayout.dialogueTextHorizontalPadding();
    double textTop = textBoxY + uiLayout.dialogueTextTopPadding();
    double textWidth = Math.max(
        60,
        textBoxWidth - uiLayout.dialogueTextHorizontalPadding() - uiLayout.dialogueTextRightPadding());
    double textHeight = Math.max(
        20,
        textBoxHeight - uiLayout.dialogueTextTopPadding() - uiLayout.dialogueTextBottomPadding());
    double textBaselineY = textTop + computeTextAscent(dialogueFont);
    gc.save();
    if (hasPolygon(dialogueTextBoundsPolygon)) {
      clipToLocalPolygon(dialogueTextBoundsPolygon, textX, textTop, textWidth, textHeight);
    } else {
      gc.beginPath();
      gc.rect(textX, textTop, textWidth, textHeight);
      gc.closePath();
      gc.clip();
    }
    drawStyledText(spans, revealedLength, textX, textBaselineY, textWidth, dialogueTextXAlign);
    gc.restore();

    // Draw continue indicator if text is fully revealed
    if (revealedLength >= plainLength && state.isWaitingForInput()) {
      drawContinueIndicator(textBoxX + textBoxWidth - 30, textBoxY + textBoxHeight - 20);
    }

    renderTextBoxButtons(textBox, width, height, hoveredButtonIndex);
  }

  private void renderNvlHistory(VnState state, double width, double height) {
    renderNvlEntries(collectNvlEntries(state, null), width, height);
  }

  private void renderNvlDialogue(DialogueLine dialogue, VnState state, double width, double height) {
    renderNvlEntries(collectNvlEntries(state, dialogue), width, height);
  }

  private void renderNvlEntries(List<DialogueRenderEntry> entries, double width, double height) {
    if (entries == null || entries.isEmpty()) return;
    double panelX = clamp(uiLayout.nvlX() * width, 0.0, width);
    double panelY = clamp(uiLayout.nvlY() * height, 0.0, height);
    double panelW = clamp(uiLayout.nvlWidth() * width, 120.0, width - panelX);
    double panelH = clamp(uiLayout.nvlHeight() * height, 80.0, height - panelY);
    double pad = uiLayout.nvlPadding();
    double speakerW = Math.max(40.0, uiLayout.nvlSpeakerWidth());
    double entryGap = Math.max(0.0, uiLayout.nvlEntryGap());
    double bodyGap = 16.0;

    drawPanel(nvlPanelImage, nvlPanelFillColor, nvlPanelOpacity, panelX, panelY, panelW, panelH, 18.0, null, 0.0);

    gc.save();
    gc.beginPath();
    gc.rect(panelX, panelY, panelW, panelH);
    gc.closePath();
    gc.clip();

    double y = panelY + pad + nameFont.getSize();
    double textX = panelX + pad + speakerW + bodyGap;
    double textWidth = Math.max(80.0, panelW - pad * 2 - speakerW - bodyGap);
    double speakerX = panelX + pad;

    for (DialogueRenderEntry entry : entries) {
      if (y > panelY + panelH) break;
      String speaker = entry.speaker() == null ? "" : entry.speaker();
      gc.setFill(nvlSpeakerTextFillColor);
      gc.setFont(nameFont);
      gc.fillText(truncateText(speaker, Math.max(20.0, speakerW - 8.0), nameFont), speakerX, y);

      String text = entry.text() == null ? "" : entry.text();
      List<TextSpan> spans = TextParser.parse(text);
      int revealed = Math.min(entry.revealedChars(), TextParser.plainLength(text));
      double bodyTop = y;
      drawStyledText(spans, revealed, textX, bodyTop, textWidth, 0.0, dialogueFont, nvlTextFillColor);
      double bodyHeight = measureStyledTextHeight(spans, revealed, textWidth, dialogueFont);
      double entryHeight = Math.max(nameFont.getSize() * 1.2, bodyHeight);
      y += entryHeight + entryGap;
    }

    gc.restore();
  }

  private void renderBubbleDialogue(DialogueLine dialogue, VnState state, double width, double height) {
    if (dialogue == null) return;
    String speaker = resolveRuntimeText(dialogue.getSpeakerName());
    String fullText = resolveRuntimeText(dialogue.getText());
    List<TextSpan> spans = TextParser.parse(fullText);
    int revealedChars = Math.min(state.getTextRevealProgress(), TextParser.plainLength(fullText));

    BubbleGeometry bubble = resolveBubbleGeometry(dialogue, state, width, height, speaker, spans, revealedChars);
    drawBubblePanel(bubble);

    double pad = uiLayout.bubbleTextPadding();
    double textX = bubble.x() + pad;
    double contentWidth = Math.max(80.0, bubble.width() - pad * 2);
    double y = bubble.y() + pad + nameFont.getSize();
    if (speaker != null && !speaker.isBlank()) {
      gc.setFill(bubbleSpeakerTextFillColor);
      gc.setFont(nameFont);
      gc.fillText(speaker, textX, y);
      y += nameFont.getSize() * 0.95;
    }
    drawStyledText(spans, revealedChars, textX, y, contentWidth, 0.0, dialogueFont, bubbleTextFillColor);

    if (revealedChars >= TextParser.plainLength(fullText) && state.isWaitingForInput()) {
      double indicatorY = bubble.tailOnTop() ? bubble.y() + bubble.height() - 14.0 : bubble.y() + bubble.height() - 18.0;
      drawContinueIndicator(bubble.x() + bubble.width() - 24.0, indicatorY);
    }
  }

  private BubbleGeometry resolveBubbleGeometry(
      DialogueLine dialogue,
      VnState state,
      double width,
      double height,
      String speaker,
      List<TextSpan> spans,
      int revealedChars
  ) {
    double maxWidth = clamp(width * uiLayout.bubbleWidthFactor(), 180.0, Math.min(width - 32.0, 620.0));
    double pad = uiLayout.bubbleTextPadding();
    double contentWidth = Math.max(120.0, maxWidth - pad * 2);
    double textHeight = measureStyledTextHeight(spans, revealedChars, contentWidth, dialogueFont);
    double speakerHeight = (speaker == null || speaker.isBlank()) ? 0.0 : nameFont.getSize() * 1.15;
    double bubbleH = Math.max(uiLayout.bubbleMinHeight(), pad * 2 + textHeight + speakerHeight);
    double tailSize = uiLayout.bubbleTailSize();

    double anchorX = width * 0.5;
    double anchorY = height * 0.58;
    String characterId = dialogue.getCharacterId();
    if (characterId != null && !characterId.isBlank()) {
      BubbleAnchor pref = state.getBubbleAnchorPreference(characterId);
      if (pref != BubbleAnchor.AUTO) {
        anchorX = switch (pref) {
          case LEFT -> width * 0.25;
          case CENTER -> width * 0.50;
          case RIGHT -> width * 0.75;
          default -> anchorX;
        };
      } else {
        CharacterPosition position = state.getCharacterPosition(characterId);
        if (position == null) position = state.getCharacterDefinedPosition(characterId);
        if (position == null) position = dialogue.getPosition();
        if (position != null) {
          anchorX = width * position.getXFraction();
          double spriteHeight = height * characterHeightFactor;
          double topY = position.computeScreenY(height, spriteHeight, characterBaselineY);
          VnState.CharacterVisual visual = state.getCharacterVisual(position);
          if (visual != null) {
            anchorX += visual.getOffsetX();
            topY += visual.getOffsetY();
          }
          anchorY = topY + spriteHeight * 0.22;
        }
      }
      anchorX += state.getBubbleOffsetXPreference(characterId);
      anchorY += state.getBubbleOffsetYPreference(characterId);
    } else if (dialogue.getPosition() != null) {
      anchorX = width * dialogue.getPosition().getXFraction();
      double spriteHeight = height * characterHeightFactor;
      anchorY = dialogue.getPosition().computeScreenY(height, spriteHeight, characterBaselineY) + spriteHeight * 0.22;
    }

    double bubbleX = clamp(anchorX - maxWidth / 2.0, 16.0, Math.max(16.0, width - maxWidth - 16.0));
    double bubbleY = anchorY - bubbleH - tailSize - uiLayout.bubbleYOffset();
    boolean tailOnTop = false;
    if (bubbleY < 16.0) {
      bubbleY = anchorY + tailSize + Math.max(8.0, uiLayout.bubbleYOffset() * 0.35);
      tailOnTop = true;
    }
    bubbleY = clamp(bubbleY, 16.0, Math.max(16.0, height - bubbleH - 16.0));
    return new BubbleGeometry(bubbleX, bubbleY, maxWidth, bubbleH, anchorX, anchorY, tailSize, tailOnTop);
  }

  private List<DialogueRenderEntry> collectNvlEntries(VnState state, DialogueLine currentDialogue) {
    List<DialogueRenderEntry> entries = new ArrayList<>();
    if (state == null) return entries;
    List<com.jvn.core.vn.VnHistory.HistoryEntry> historyEntries = state.getHistory().getEntries();
    int maxEntries = Math.max(1, uiLayout.nvlMaxEntries());
    int start = Math.max(0, historyEntries.size() - maxEntries);
    for (int i = start; i < historyEntries.size(); i++) {
      var entry = historyEntries.get(i);
      String text = entry.getText() == null ? "" : entry.getText();
      entries.add(new DialogueRenderEntry(entry.getSpeaker(), text, TextParser.plainLength(text)));
    }
    if (currentDialogue != null && !entries.isEmpty()) {
      String text = resolveRuntimeText(currentDialogue.getText());
      DialogueRenderEntry last = entries.get(entries.size() - 1);
      entries.set(entries.size() - 1, new DialogueRenderEntry(
          resolveRuntimeText(currentDialogue.getSpeakerName()),
          text,
          Math.min(state.getTextRevealProgress(), TextParser.plainLength(text))));
    }
    return entries;
  }

  private void renderTextBoxButtons(TextBoxGeometry textBox, double viewportWidth, double viewportHeight, int hoveredButtonIndex) {
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return;
    for (int i = 0; i < textBoxButtons.size(); i++) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox, viewportWidth, viewportHeight);
      boolean hovered = i == hoveredButtonIndex;
      boolean enabled = button.enabled();

      Image asset = firstNonBlank(button.assetPath(), null) != null ? loadImage(button.assetPath()) : null;
      Image hoverAsset = firstNonBlank(button.hoverAssetPath(), null) != null ? loadImage(button.hoverAssetPath()) : asset;
      Image disabledAsset = firstNonBlank(button.disabledAssetPath(), null) != null ? loadImage(button.disabledAssetPath()) : asset;
      Image drawAsset = !enabled
          ? firstNonNull(disabledAsset, asset)
          : (hovered ? firstNonNull(hoverAsset, asset) : asset);
      boolean imageBacked = drawAsset != null;
      List<BoundsPointCodec.Point> buttonPolygon = parseBoundsPoints(button.boundsPoints());
      boolean clipButton = hasPolygon(buttonPolygon);
      if (imageBacked) {
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
  }

  private record StyledGlyph(char value, Font font, Color color, TextEffect effect, int glyphIndex, double width) {}

  private record StyledLine(List<StyledGlyph> glyphs, double width) {}

  private void drawStyledText(List<TextSpan> spans, int revealedChars, double startX, double startY, double maxWidth, double xAlign) {
    drawStyledText(spans, revealedChars, startX, startY, maxWidth, xAlign, dialogueFont, dialogueTextFillColor);
  }

  private void drawStyledText(
      List<TextSpan> spans,
      int revealedChars,
      double startX,
      double startY,
      double maxWidth,
      double xAlign,
      Font baseFont,
      Color defaultTextColor
  ) {
    List<StyledLine> lines = layoutStyledLines(spans, revealedChars, maxWidth, baseFont, defaultTextColor);
    drawStyledLines(lines, startX, startY, maxWidth, xAlign, baseFont, defaultTextColor);
  }

  private List<StyledLine> layoutStyledLines(
      List<TextSpan> spans,
      int revealedChars,
      double maxWidth,
      Font baseFont,
      Color defaultTextColor
  ) {
    gc.setFont(baseFont);
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
        Color spanColor = span.hasColor() ? parseColorHex(span.getColorHex()) : defaultTextColor;
        Font effectFont = baseFont;
        if (span.getEffect() == TextEffect.BOLD) {
          effectFont = Font.font(baseFont.getFamily(), FontWeight.BOLD, baseFont.getSize());
        } else if (span.getEffect() == TextEffect.ITALIC) {
          effectFont = Font.font(baseFont.getFamily(), FontWeight.NORMAL, baseFont.getSize());
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
    return lines;
  }

  private void drawStyledLines(
      List<StyledLine> lines,
      double startX,
      double startY,
      double maxWidth,
      double xAlign,
      Font baseFont,
      Color defaultTextColor
  ) {
    double lineHeight = Math.max(22.0, baseFont.getSize() * 1.15);
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

    gc.setFont(baseFont);
    gc.setFill(defaultTextColor);
  }

  private double measureStyledTextHeight(List<TextSpan> spans, int revealedChars, double maxWidth, Font baseFont) {
    List<StyledLine> lines = layoutStyledLines(spans, revealedChars, maxWidth, baseFont, dialogueTextFillColor);
    double lineHeight = Math.max(22.0, baseFont.getSize() * 1.15);
    return Math.max(lineHeight, lines.size() * lineHeight);
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
    narrationTextBoxImage = loadImage(resolved.textBoxNarrationAssetPath());
    nameBoxImage = loadImage(resolved.nameBoxAssetPath());
    nvlPanelImage = loadImage(resolved.nvlPanelAssetPath());
    bubbleImage = loadImage(resolved.bubbleAssetPath());
    textBoxFillColor = parseColor(resolved.textBoxColor(), TEXTBOX_COLOR);
    nameBoxFillColor = parseColor(resolved.nameBoxColor(), NAME_BOX_COLOR);
    nameTextFillColor = parseColor(resolved.nameTextColor(), Color.web("#FFD78A"));
    dialogueTextFillColor = parseColor(resolved.dialogueTextColor(), TEXT_COLOR);
    nvlPanelFillColor = parseColor(resolved.nvlPanelColor(), Color.web("#08111acc"));
    nvlSpeakerTextFillColor = parseColor(resolved.nvlSpeakerTextColor(), Color.web("#F7D89A"));
    nvlTextFillColor = parseColor(resolved.nvlTextColor(), dialogueTextFillColor);
    bubbleFillColor = parseColor(resolved.bubbleColor(), Color.web("#152238ee"));
    bubbleBorderFillColor = parseColor(resolved.bubbleBorderColor(), Color.web("#A9BCD9"));
    bubbleSpeakerTextFillColor = parseColor(resolved.bubbleSpeakerTextColor(), Color.web("#FFD78A"));
    bubbleTextFillColor = parseColor(resolved.bubbleTextColor(), dialogueTextFillColor);
    textBoxAssetOverlayOpacity = clamp(
        resolved.textBoxOpacity() == null ? 0.88 : resolved.textBoxOpacity(),
        0.0,
        1.0
    );
    nvlPanelOpacity = clamp(
        resolved.nvlPanelOpacity() == null ? 0.84 : resolved.nvlPanelOpacity(),
        0.0,
        1.0
    );
    bubbleOpacity = clamp(
        resolved.bubbleOpacity() == null ? 0.96 : resolved.bubbleOpacity(),
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
    bubbleCornerRadius = clamp(resolved.bubbleCornerRadius(), 0.0, 96.0);
    bubbleBorderWidth = clamp(resolved.bubbleBorderWidth(), 0.0, 12.0);
    nameTextXAlign = clamp(resolved.nameTextXAlign() == null ? 0.0 : resolved.nameTextXAlign(), 0.0, 1.0);
    dialogueTextXAlign = clamp(resolved.dialogueTextXAlign() == null ? 0.0 : resolved.dialogueTextXAlign(), 0.0, 1.0);
    choiceTextXAlign = clamp(resolved.choiceTextXAlign() == null ? 0.0 : resolved.choiceTextXAlign(), 0.0, 1.0);

    // Apply font settings from style spec (font weight from spec, with sensible defaults)
    String nameFontFamily = resolved.nameTextFontFamily() != null ? resolved.nameTextFontFamily() : DEFAULT_FONT_FAMILY;
    int nameFontSize = resolved.nameTextFontSize() != null ? resolved.nameTextFontSize() : DEFAULT_NAME_FONT_SIZE;
    FontWeight nameFontWeight = parseFontWeight(resolved.nameTextFontWeight(), FontWeight.BOLD);
    this.nameFont = ProjectFontResolver.resolve(projectRoot, nameFontFamily, nameFontWeight, nameFontSize, DEFAULT_FONT_FAMILY);

    String dialogueFontFamily = resolved.dialogueTextFontFamily() != null ? resolved.dialogueTextFontFamily() : DEFAULT_FONT_FAMILY;
    int dialogueFontSize = resolved.dialogueTextFontSize() != null ? resolved.dialogueTextFontSize() : DEFAULT_DIALOGUE_FONT_SIZE;
    FontWeight dialogueFontWeight = parseFontWeight(resolved.dialogueTextFontWeight(), FontWeight.NORMAL);
    this.dialogueFont = ProjectFontResolver.resolve(projectRoot, dialogueFontFamily, dialogueFontWeight, dialogueFontSize, DEFAULT_FONT_FAMILY);

    String choiceFontFamily = resolved.choiceFontFamily() != null ? resolved.choiceFontFamily() : DEFAULT_FONT_FAMILY;
    int choiceFontSize = resolved.choiceFontSize() != null ? resolved.choiceFontSize() : DEFAULT_CHOICE_FONT_SIZE;
    FontWeight choiceFontWeightVal = parseFontWeight(resolved.choiceFontWeight(), FontWeight.NORMAL);
    this.choiceFont = ProjectFontResolver.resolve(projectRoot, choiceFontFamily, choiceFontWeightVal, choiceFontSize, DEFAULT_FONT_FAMILY);

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

  private String readStringVariable(VnState state, String key) {
    if (state == null || key == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }

  private Boolean readBooleanVariable(VnState state, String key) {
    if (state == null || key == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.doubleValue() != 0.0;
    if (value instanceof String s) {
      String text = s.trim();
      if (text.isEmpty()) return null;
      return VnAudioVisualizerConfig.isTruthy(text);
    }
    return null;
  }

  private boolean isAudioVisualizerEnabled() {
    return currentState != null && VnAudioVisualizerConfig.isTruthy(currentState.getVariables().get(VnAudioVisualizerConfig.VAR_ENABLED));
  }

  private int resolveAudioVisualizerBarCount() {
    Double override = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_BARS);
    if (override == null) return VnAudioVisualizerConfig.DEFAULT_BARS;
    return VnAudioVisualizerConfig.clampBars((int) Math.round(override));
  }

  private AudioVisualizerSettings resolveAudioVisualizerSettings() {
    String style = VnAudioVisualizerConfig.normalizeStyle(readStringVariable(currentState, VnAudioVisualizerConfig.VAR_STYLE));
    String colorToken = readStringVariable(currentState, VnAudioVisualizerConfig.VAR_COLOR);
    String accentToken = readStringVariable(currentState, VnAudioVisualizerConfig.VAR_ACCENT);
    Double alphaValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_ALPHA);
    Double heightValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_HEIGHT);
    Double zValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_Z);
    Boolean glowValue = readBooleanVariable(currentState, VnAudioVisualizerConfig.VAR_GLOW);
    return new AudioVisualizerSettings(
        isAudioVisualizerEnabled(),
        resolveAudioVisualizerBarCount(),
        style,
        colorToken,
        accentToken,
        alphaValue == null ? VnAudioVisualizerConfig.DEFAULT_ALPHA : VnAudioVisualizerConfig.clampAlpha(alphaValue),
        glowValue == null || glowValue,
        heightValue == null ? VnAudioVisualizerConfig.DEFAULT_HEIGHT : VnAudioVisualizerConfig.clampHeight(heightValue),
        zValue == null ? VnAudioVisualizerConfig.DEFAULT_Z : (int) Math.round(zValue));
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
      double textBaselineY = uiLayout.choiceTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              y,
              geo.choiceHeight(),
              uiLayout.choiceTextTopPadding(),
              uiLayout.choiceTextBottomPadding(),
              choiceFont,
              uiLayout.choiceTextYAlign())
          : y + geo.choiceHeight() / 2 + choiceTextBaselineOffset;
      gc.fillText(
          choiceText,
          resolveAlignedTextX(contentX, contentWidth, textWidth, choiceTextXAlign),
          textBaselineY
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

  private void drawPanel(
      Image asset,
      Color fillColor,
      double opacity,
      double x,
      double y,
      double width,
      double height,
      double radius,
      Color borderColor,
      double borderWidth
  ) {
    double prevAlpha = gc.getGlobalAlpha();
    gc.setGlobalAlpha(prevAlpha * clamp(opacity, 0.0, 1.0));
    if (asset != null) {
      gc.drawImage(asset, x, y, width, height);
    } else {
      gc.setFill(fillColor);
      gc.fillRoundRect(x, y, width, height, radius, radius);
    }
    gc.setGlobalAlpha(prevAlpha);
    if (borderColor != null && borderWidth > 0.0) {
      gc.setStroke(borderColor);
      gc.setLineWidth(borderWidth);
      gc.strokeRoundRect(x, y, width, height, radius, radius);
    }
  }

  private void drawBubblePanel(BubbleGeometry bubble) {
    if (bubble == null) return;
    drawPanel(
        bubbleImage,
        bubbleFillColor,
        bubbleOpacity,
        bubble.x(),
        bubble.y(),
        bubble.width(),
        bubble.height(),
        bubbleCornerRadius,
        bubbleBorderFillColor,
        bubbleBorderWidth
    );

    double tailHalf = bubble.tailSize() * 0.58;
    double tailCenter = clamp(
        bubble.anchorX(),
        bubble.x() + bubble.tailSize(),
        bubble.x() + bubble.width() - bubble.tailSize());
    double baseY = bubble.tailOnTop() ? bubble.y() : bubble.y() + bubble.height();
    double tipY = bubble.anchorY();
    double[] xs = new double[] {tailCenter - tailHalf, tailCenter + tailHalf, bubble.anchorX()};
    double[] ys = bubble.tailOnTop()
        ? new double[] {baseY, baseY, tipY}
        : new double[] {baseY, baseY, tipY};
    gc.setFill(withOpacity(bubbleFillColor, bubbleOpacity));
    gc.fillPolygon(xs, ys, 3);
    gc.setStroke(bubbleBorderFillColor);
    gc.setLineWidth(bubbleBorderWidth);
    gc.strokePolygon(xs, ys, 3);
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

  private double computeTextAscent(Font font) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text("Hg");
    helper.setFont(font);
    double ascent = -helper.getLayoutBounds().getMinY();
    return ascent > 0.0 ? ascent : Math.max(1.0, font.getSize() * 0.8);
  }

  private double computeTextHeight(Font font) {
    javafx.scene.text.Text helper = new javafx.scene.text.Text("Hg");
    helper.setFont(font);
    double height = helper.getLayoutBounds().getHeight();
    return height > 0.0 ? height : Math.max(1.0, font.getSize());
  }

  private double resolvePaddedTextBaselineY(
      double boxY,
      double boxHeight,
      double topPadding,
      double bottomPadding,
      Font font,
      double yAlign
  ) {
    double contentTop = boxY + Math.max(0.0, topPadding);
    double contentHeight = Math.max(1.0, boxHeight - Math.max(0.0, topPadding) - Math.max(0.0, bottomPadding));
    double textHeight = computeTextHeight(font);
    double ascent = computeTextAscent(font);
    double clampedAlign = clamp(yAlign, 0.0, 1.0);
    double extra = Math.max(0.0, contentHeight - textHeight);
    return contentTop + ascent + extra * clampedAlign;
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

  public VnOverlayButtonSpec getHoveredOverlayButton(VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || !state.hasOverlayScreens()) return null;
    List<VnOverlayScreenSpec> screens = state.getOverlayScreens();
    for (int screenIndex = screens.size() - 1; screenIndex >= 0; screenIndex--) {
      VnOverlayScreenSpec screen = screens.get(screenIndex);
      if (screen == null) continue;
      ScreenGeometry screenGeometry = computeOverlayScreenGeometry(screen, width, height);
      List<VnOverlayButtonSpec> buttons = screen.getButtons();
      for (int i = buttons.size() - 1; i >= 0; i--) {
        VnOverlayButtonSpec button = buttons.get(i);
        if (button == null || !button.enabled()) continue;
        ButtonGeometry geometry = computeOverlayButtonGeometry(button, screenGeometry, width, height);
        if (geometry.contains(mouseX, mouseY)) return button;
      }
      if (screen.isModal()) break;
    }
    return null;
  }

  private int getHoveredTextBoxButtonIndex(VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || state.isUiHidden()) return -1;
    if (state.getDialoguePresentationMode() != DialoguePresentationMode.STANDARD) return -1;
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    VnNode currentNode = state.getCurrentNode();
    if (currentNode == null || currentNode.getType() != VnNodeType.DIALOGUE) return -1;

    TextBoxGeometry textBox = computeTextBoxGeometry(width, height);
    for (int i = textBoxButtons.size() - 1; i >= 0; i--) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null || !button.enabled()) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox, width, height);
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

  private ButtonGeometry computeButtonGeometry(
      VnUiActionButtonSpec button,
      TextBoxGeometry textBox,
      double viewportWidth,
      double viewportHeight
  ) {
    double baseX = button.viewportSpace() ? 0.0 : textBox.x();
    double baseY = button.viewportSpace() ? 0.0 : textBox.y();
    double baseW = button.viewportSpace() ? Math.max(1.0, viewportWidth) : textBox.width();
    double baseH = button.viewportSpace() ? Math.max(1.0, viewportHeight) : textBox.height();
    double x = baseX + baseW * button.x();
    double y = baseY + baseH * button.y();
    double width = Math.max(8, baseW * button.width());
    double height = Math.max(8, baseH * button.height());
    return new ButtonGeometry(x, y, width, height);
  }

  private void renderOverlayScreens(VnState state, double width, double height, VnOverlayButtonSpec hoveredButton) {
    if (state == null || !state.hasOverlayScreens()) return;
    boolean dimDrawn = false;
    for (VnOverlayScreenSpec screen : state.getOverlayScreens()) {
      if (screen == null) continue;
      if (screen.isDimBackground() && !dimDrawn) {
        gc.setFill(Color.rgb(0, 0, 0, 0.42));
        gc.fillRect(0, 0, width, height);
        dimDrawn = true;
      }
      ScreenGeometry screenGeometry = computeOverlayScreenGeometry(screen, width, height);
      renderOverlayPanel(screen, screenGeometry, width, height);
      for (VnOverlayButtonSpec button : screen.getButtons()) {
        if (button == null || !button.enabled()) continue;
        ButtonGeometry geometry = computeOverlayButtonGeometry(button, screenGeometry, width, height);
        renderOverlayButton(button, geometry, hoveredButton == button);
      }
    }
  }

  private void renderOverlayPanel(VnOverlayScreenSpec screen, ScreenGeometry geometry, double viewportWidth, double viewportHeight) {
    if (screen == null || geometry == null) return;
    gc.setFill(Color.rgb(18, 21, 28, 0.95));
    gc.fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 22, 22);
    gc.setStroke(Color.rgb(210, 220, 240, 0.22));
    gc.setLineWidth(1.5);
    gc.strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 22, 22);

    double innerX = geometry.x() + 22;
    double innerY = geometry.y() + 20;
    double innerWidth = Math.max(40, geometry.width() - 44);
    gc.setFill(Color.WHITE);
    gc.setFont(Font.font(nameFont.getFamily(), FontWeight.BOLD, 22));
    gc.fillText(screen.getTitle(), innerX, innerY + 4);

    if (screen.getText() != null && !screen.getText().isBlank()) {
      gc.setFill(Color.rgb(228, 232, 240, 0.95));
      gc.setFont(Font.font(dialogueFont.getFamily(), FontWeight.NORMAL, 17));
      double textY = innerY + 34;
      for (String line : wrapText(screen.getText(), innerWidth, gc.getFont())) {
        gc.fillText(line, innerX, textY);
        textY += 22;
        if (textY > geometry.y() + geometry.height() - 44) break;
      }
    }

    if (screen.getTimerRemainingMs() > 0) {
      double ratio = Math.max(0.0, Math.min(1.0, screen.getTimerRemainingMs() / 5000.0));
      gc.setFill(Color.rgb(82, 210, 255, 0.65));
      gc.fillRoundRect(
          geometry.x() + 18,
          geometry.y() + geometry.height() - 12,
          Math.max(18, (geometry.width() - 36) * ratio),
          4,
          4,
          4
      );
    }
  }

  private void renderOverlayButton(VnOverlayButtonSpec button, ButtonGeometry geometry, boolean hovered) {
    if (button == null || geometry == null) return;
    Color fill = hovered ? Color.rgb(74, 122, 214, 0.92) : Color.rgb(43, 49, 60, 0.92);
    Color stroke = hovered ? Color.rgb(144, 192, 255, 0.95) : Color.rgb(255, 255, 255, 0.18);
    gc.setFill(fill);
    gc.fillRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 16, 16);
    gc.setStroke(stroke);
    gc.setLineWidth(1.2);
    gc.strokeRoundRect(geometry.x(), geometry.y(), geometry.width(), geometry.height(), 16, 16);
    if (button.label() != null && !button.label().isBlank()) {
      gc.setFill(Color.WHITE);
      gc.setFont(Font.font(choiceFont.getFamily(), FontWeight.NORMAL, 16));
      gc.fillText(button.label(), geometry.x() + 12, geometry.y() + geometry.height() * 0.62);
    }
  }

  private List<String> wrapText(String text, double maxWidth, Font font) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank()) return lines;
    String[] words = text.split("\\s+");
    StringBuilder current = new StringBuilder();
    gc.setFont(font);
    for (String word : words) {
      String candidate = current.length() == 0 ? word : (current + " " + word);
      if (measureText(candidate) <= maxWidth || current.length() == 0) {
        current.setLength(0);
        current.append(candidate);
      } else {
        lines.add(current.toString());
        current.setLength(0);
        current.append(word);
      }
    }
    if (current.length() > 0) lines.add(current.toString());
    return lines;
  }

  private double measureText(String text) {
    if (text == null || text.isEmpty()) return 0.0;
    Text probe = new Text(text);
    probe.setFont(gc.getFont());
    return probe.getLayoutBounds().getWidth();
  }

  private ScreenGeometry computeOverlayScreenGeometry(VnOverlayScreenSpec screen, double viewportWidth, double viewportHeight) {
    double x = clamp(viewportWidth * screen.getX(), 0, viewportWidth);
    double y = clamp(viewportHeight * screen.getY(), 0, viewportHeight);
    double width = clamp(viewportWidth * screen.getWidth(), 40, viewportWidth - x);
    double height = clamp(viewportHeight * screen.getHeight(), 40, viewportHeight - y);
    return new ScreenGeometry(x, y, width, height);
  }

  private ButtonGeometry computeOverlayButtonGeometry(
      VnOverlayButtonSpec button,
      ScreenGeometry screen,
      double viewportWidth,
      double viewportHeight
  ) {
    double baseX = button.viewportSpace() ? 0.0 : screen.x();
    double baseY = button.viewportSpace() ? 0.0 : screen.y();
    double baseW = button.viewportSpace() ? viewportWidth : screen.width();
    double baseH = button.viewportSpace() ? viewportHeight : screen.height();
    double x = baseX + baseW * button.x();
    double y = baseY + baseH * button.y();
    double width = Math.max(8, baseW * button.width());
    double height = Math.max(8, baseH * button.height());
    return new ButtonGeometry(x, y, width, height);
  }

  private record ChoiceGeometry(double choiceX, double startY, double choiceWidth, double choiceHeight, double choiceGap) {}
  private record TextBoxGeometry(double x, double y, double width, double height) {}
  private record ScreenGeometry(double x, double y, double width, double height) {}
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
