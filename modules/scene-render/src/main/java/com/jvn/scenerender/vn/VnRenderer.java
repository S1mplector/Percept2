package com.jvn.scenerender.vn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.jvn.core.accessibility.AccessibilityThemeLoader;
import com.jvn.core.accessibility.NoopTextToSpeechService;
import com.jvn.core.accessibility.TextToSpeechService;
import com.jvn.core.audio.AudioFacade;
import com.jvn.core.diagnostics.DrawCallStats;
import com.jvn.core.localization.Localization;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.DialoguePresentationMode;
import com.jvn.core.vn.VnAudioVisualizerConfig;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnParticleCommand;
import com.jvn.core.vn.VnParticlePresetLibrary;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnVariableInterpolator;
import com.jvn.core.vn.VnErrorOverlay;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.ui.VnOverlayButtonSpec;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import org.jspecify.annotations.Nullable;

/**
 * Renders visual novel elements through the platform-agnostic {@link Blitter2D} drawing
 * abstraction, ported from the original monolithic JavaFX {@code GraphicsContext}-bound {@code
 * VnRenderer} in {@code modules/fx}.
 *
 * <p>This is a thin facade delegating to focused collaborators built during the same retrofit:
 * {@link VnDialogueRenderer} (text-box/NVL/bubble dialogue), {@link VnCharacterCompositor}
 * (character sprite compositing/drawing), {@link VnStageLightingRenderer} (stage-lit
 * background/character compositing and light overlays), {@link VnAudioVisualizerRenderer} (audio
 * visualizer bars), {@link VnChoiceOverlayRenderer} (choice list, overlay screens, mode
 * indicators, error overlay), and {@link VnTransitionRenderer} (background crossfade/slide/wipe,
 * transition overlays, flash overlay). Every theme/font/color field only a given collaborator
 * needs is owned here and passed down per-call, the same dependency-injection style
 * {@code MenuRenderer} established for its own collaborators.
 *
 * <h2>Port notes</h2>
 * <ul>
 *   <li>The only public API change from the original is the constructor:
 *   {@code VnRenderer(Blitter2D)} replaces {@code VnRenderer(GraphicsContext)}. Every other public
 *   method signature, and every VN-domain type, is unchanged.</li>
 *   <li>{@code gc.save()/restore()} become {@code blitter.push()/pop()}; JavaFX {@code Color}s
 *   become normalised {@code double[4]} RGBA; {@code Font} becomes {@link VnFontSpec}; {@code
 *   Image} becomes a classpath/filesystem path string that {@link Blitter2D#drawImage} resolves.</li>
 *   <li>Per-pixel stage lighting and multi-layer sprite compositing moved entirely into {@link
 *   VnStageLightingRenderer}/{@link VnCharacterCompositor}; this facade never touches pixels.</li>
 *   <li>Background image loading/caching is retired (per the design's decision #1): backgrounds
 *   draw straight through {@link Blitter2D#drawImage}, which resolves+caches internally, exactly
 *   as {@link VnTransitionRenderer}'s {@code BackgroundPathResolver} contract expects.</li>
 * </ul>
 */
public class VnRenderer {
  private static final long MIB = 1024L * 1024L;
  static final long SOURCE_IMAGE_CACHE_BUDGET_BYTES = 96L * MIB;
  static final long COMPOSITE_SPRITE_CACHE_BUDGET_BYTES = 96L * MIB;
  // Retained for reference only; background image caching itself is retired under this port
  // (backgrounds draw straight through Blitter2D.drawImage, which caches internally).
  static final long BACKGROUND_IMAGE_CACHE_BUDGET_BYTES = 48L * MIB;
  static final long STAGE_BACKGROUND_CACHE_BUDGET_BYTES = 48L * MIB;
  static final long STAGE_CHARACTER_CACHE_BUDGET_BYTES = 64L * MIB;

  private static final String DEFAULT_FONT_FAMILY = "SansSerif";
  private static final int DEFAULT_NAME_FONT_SIZE = 18;
  private static final int DEFAULT_DIALOGUE_FONT_SIZE = 22;
  private static final int DEFAULT_CHOICE_FONT_SIZE = 20;
  private static final double DEFAULT_CHARACTER_HEIGHT_FACTOR = 0.85;
  private static final double DEFAULT_CHARACTER_BASELINE_Y = 1.0;

  private static final double[] TEXTBOX_COLOR = {12.0 / 255, 18.0 / 255, 32.0 / 255, 0.88};
  private static final double[] NAME_BOX_COLOR = {20.0 / 255, 32.0 / 255, 56.0 / 255, 0.56};
  private static final double[] TEXT_COLOR = {0xE8 / 255.0, 0xED / 255.0, 0xF6 / 255.0, 1.0};
  private static final double[] DEFAULT_NAME_TEXT_COLOR = {1.0, 0xD7 / 255.0, 0x8A / 255.0, 1.0};
  private static final double[] CHOICE_BG_COLOR = parseHex("#1A2640D8");
  private static final double[] CHOICE_HOVER_COLOR = parseHex("#243358E8");
  private static final double[] CHOICE_DISABLED_COLOR = parseHex("#121826A0");
  private static final double[] CHOICE_DISABLED_BORDER_COLOR = parseHex("#28345060");
  private static final double[] TEXT_COLOR_DISABLED = parseHex("#6878A0");
  private static final double DEFAULT_CHOICE_RADIUS = 8.0;
  private static final double DEFAULT_CHOICE_BORDER_WIDTH = 1.5;
  private static final double DEFAULT_CHOICE_TEXT_BASELINE_OFFSET = 4.0;

  private static final String VAR_CHARACTER_HEIGHT_FACTOR = "ui.characterHeightFactor";
  private static final String VAR_CHARACTER_BASELINE_Y = "ui.characterBaselineY";
  private static final String VAR_DIALOGUE_FADE_MS = "ui.dialogueFadeMs";
  private static final String VAR_DIALOGUE_UI = "ui.dialogueUi";
  private static final String VAR_DIALOGUE_STYLE = "ui.dialogueStyle";
  private static final String VAR_TEXT_BOX_ASSET = "ui.textBoxAsset";
  private static final String VAR_TEXT_BOX_ASSET_ENABLED = "ui.textBoxAssetEnabled";
  private static final String VAR_TEXT_BOX_BUTTONS = "ui.textBoxButtons";
  private static final String VAR_TEXT_BOX_BUTTONS_ENABLED = "ui.textBoxButtonsEnabled";

  private final Blitter2D blitter;
  private final VnDialogueRenderer dialogue;
  private final VnCharacterCompositor characters;
  private final VnStageLightingRenderer stageLighting;
  private final VnAudioVisualizerRenderer audioVisualizer;
  private final VnChoiceOverlayRenderer choicesOverlays;
  private final VnTransitionRenderer transitions;
  private final ParticleEmitter2D particleEmitter = new ParticleEmitter2D();
  private final DrawCallStats drawCallStats = new DrawCallStats();

  private TextToSpeechService tts = new NoopTextToSpeechService();
  private @Nullable String lastTtsNodeId;
  private boolean textToSpeechEnabled;

  private @Nullable File projectRoot;
  private @Nullable VnState currentState;
  private long animationTime = 0;
  private @Nullable AudioFacade audioFacade;
  private VnUiLayoutSpec uiLayout;
  private VnUiStyleSpec uiStyle = VnUiStyleSpec.defaults();
  private AccessibilityThemeLoader accessibilityTheme = AccessibilityThemeLoader.load("none");
  private String appliedAccessibilityThemeName = "none";
  private double appliedUiFontScale = 1.0;
  private List<VnUiActionButtonSpec> textBoxButtons = List.of();
  private @Nullable VnCharacterSceneAccessor timelineAccessor;
  private boolean freezeTransientEffects;
  private boolean disposed;

  // Theme-derived state applied by applyUiStyle(), owned here and handed to collaborators per-call.
  private VnFontSpec nameFont = new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_NAME_FONT_SIZE, true);
  private VnFontSpec dialogueFont = new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_DIALOGUE_FONT_SIZE, false);
  private VnFontSpec choiceFont = new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_CHOICE_FONT_SIZE, false);

  private @Nullable String textBoxImagePath;
  private @Nullable String narrationTextBoxImagePath;
  private @Nullable String nameBoxImagePath;
  private @Nullable String nvlPanelImagePath;
  private @Nullable String bubbleImagePath;
  private final String continueIndicatorImagePath = "assets/ui/dialogue/ctc_marker.png";
  private double[] textBoxFillColor = TEXTBOX_COLOR;
  private double[] nameBoxFillColor = NAME_BOX_COLOR;
  private double[] nameTextFillColor = DEFAULT_NAME_TEXT_COLOR;
  private double[] dialogueTextFillColor = TEXT_COLOR;
  private double[] nvlPanelFillColor = parseHex("#08111ACC");
  private double[] nvlSpeakerTextFillColor = parseHex("#F7D89A");
  private double[] nvlTextFillColor = TEXT_COLOR;
  private double[] bubbleFillColor = parseHex("#152238EE");
  private double[] bubbleBorderFillColor = parseHex("#A9BCD9");
  private double[] bubbleSpeakerTextFillColor = parseHex("#FFD78A");
  private double[] bubbleTextFillColor = TEXT_COLOR;
  private double textBoxAssetOverlayOpacity = 0.28;
  private double nameBoxRenderOpacity = 1.0;
  private double nvlPanelOpacity = 0.84;
  private double bubbleOpacity = 0.96;
  private double nameTextXAlign = 0.0;
  private double dialogueTextXAlign = 0.0;
  private double choiceTextXAlign = 0.0;
  private List<BoundsPointCodec.Point> textBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> nameBoxBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> dialogueTextBoundsPolygon = List.of();
  private List<BoundsPointCodec.Point> choiceButtonBoundsPolygon = List.of();

  private @Nullable String choiceButtonImagePath;
  private @Nullable String choiceButtonHoverImagePath;
  private @Nullable String choiceButtonDisabledImagePath;
  private double[] choiceBgColor = CHOICE_BG_COLOR;
  private double[] choiceHoverColor = CHOICE_HOVER_COLOR;
  private double[] choiceDisabledColor = CHOICE_DISABLED_COLOR;
  private double[] choiceTextColor = TEXT_COLOR;
  private double[] choiceHoverTextColor = TEXT_COLOR;
  private double[] choiceDisabledTextColor = TEXT_COLOR_DISABLED;
  private double[] choiceBorderColor = TEXT_COLOR;
  private double[] choiceHoverBorderColor = TEXT_COLOR;
  private double[] choiceDisabledBorderColor = CHOICE_DISABLED_BORDER_COLOR;
  private double choiceCornerRadius = DEFAULT_CHOICE_RADIUS;
  private double choiceBorderWidth = DEFAULT_CHOICE_BORDER_WIDTH;
  private double choiceTextBaselineOffset = DEFAULT_CHOICE_TEXT_BASELINE_OFFSET;

  private double styleCharacterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double styleCharacterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;
  private double characterHeightFactor = DEFAULT_CHARACTER_HEIGHT_FACTOR;
  private double characterBaselineY = DEFAULT_CHARACTER_BASELINE_Y;

  // Dialogue fade state.
  private @Nullable VnState dialogueFadeState;
  private @Nullable DialogueLine dialogueFadeLine;
  private boolean dialogueFadeVisible;
  private double dialogueFadeAlpha;
  private double dialogueFadeFrom;
  private double dialogueFadeTarget;
  private long dialogueFadeStartedAtNanos;

  // Particle integration.
  private @Nullable VnParticleCommand renderedParticleCommand;
  private long particleLastFrameNanos = 0L;
  private int lastParticleLayer = 100;
  private double particleConfigWidth = -1.0;
  private double particleConfigHeight = -1.0;

  public VnRenderer(Blitter2D blitter) {
    this.blitter = blitter;
    this.dialogue = new VnDialogueRenderer(blitter);
    this.characters = new VnCharacterCompositor(blitter);
    this.stageLighting = new VnStageLightingRenderer(blitter);
    this.audioVisualizer = new VnAudioVisualizerRenderer(blitter);
    this.choicesOverlays = new VnChoiceOverlayRenderer(blitter);
    this.transitions = new VnTransitionRenderer(blitter);
    this.characters.setStageLightingRenderer(this.stageLighting);
    resetParticleState();
    this.uiLayout = VnUiLayoutSpec.defaults();
    reloadUiLayout();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Collaborator wiring / lifecycle
  // ─────────────────────────────────────────────────────────────────────────

  public void setTimelineAccessor(@Nullable VnCharacterSceneAccessor accessor) {
    this.timelineAccessor = accessor;
    this.characters.setTimelineAccessor(accessor);
  }

  public void setAudioFacade(@Nullable AudioFacade facade) {
    this.audioFacade = facade;
  }

  public void setTextToSpeechService(@Nullable TextToSpeechService tts) {
    this.tts = tts == null ? new NoopTextToSpeechService() : tts;
  }

  public void setProjectRoot(File root) {
    if (Objects.equals(this.projectRoot, root)) return;
    this.projectRoot = root;
    characters.setProjectRoot(root);
    stageLighting.setProjectRoot(root);
    reloadUiLayout();
  }

  public VnUiLayoutSpec getUiLayout() {
    return uiLayout;
  }

  public void resetParticleState() {
    particleEmitter.clear();
    particleEmitter.setEmitting(false);
    particleEmitter.setEmissionRate(0);
    renderedParticleCommand = null;
    particleLastFrameNanos = 0L;
    lastParticleLayer = 100;
    particleConfigWidth = -1.0;
    particleConfigHeight = -1.0;
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

  public void setAccessibilityTheme(String themeName) {
    this.appliedAccessibilityThemeName = normalizeAccessibilityThemeName(themeName);
    this.accessibilityTheme = AccessibilityThemeLoader.load(appliedAccessibilityThemeName);
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

  public DrawCallStats getDrawCallStats() {
    return drawCallStats;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Render orchestration
  // ─────────────────────────────────────────────────────────────────────────

  public void render(VnState state, VnScenario scenario, double width, double height) {
    drawCallStats.reset();
    this.currentState = state;
    syncAccessibilitySettings(state);
    applyRuntimeCharacterFramingOverrides(state);
    VnStagePreset activeStage = resolveActiveStagePreset(state, scenario);

    blitter.setFill(0, 0, 0, 1);
    blitter.fillRect(0, 0, width, height);

    double shakeMagnitude = state.getScreenShakeMagnitude();
    boolean shaking = shakeMagnitude > 0.01;
    if (shaking) {
      double t = System.currentTimeMillis() * 0.02;
      blitter.push();
      blitter.translate(Math.sin(t * 2.3) * shakeMagnitude, Math.cos(t * 1.7) * shakeMagnitude);
    }

    boolean handledTransitionBackground = activeStage != null
        && activeStage.getBackgroundTag() != null
        && !activeStage.getBackgroundTag().isBlank();
    var transition = handledTransitionBackground ? null : state.getActiveTransition();
    if (transition != null) {
      switch (transition.getType()) {
        case CROSSFADE -> {
          String prevId = state.getPreviousBackgroundIdDuringTransition();
          String curId = state.getCurrentBackgroundId();
          if (prevId != null && curId != null) {
            transitions.renderCrossfadeBackground(
                scenario.getBackground(prevId), scenario.getBackground(curId),
                state.getTransitionProgress(), width, height, this::resolveBackgroundResolverPath);
            handledTransitionBackground = true;
          }
        }
        case SLIDE_LEFT -> {
          transitions.renderSlideBackground(
              scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
              scenario.getBackground(state.getCurrentBackgroundId()),
              state.getTransitionProgress(), width, height, true, this::resolveBackgroundResolverPath);
          handledTransitionBackground = true;
        }
        case SLIDE_RIGHT -> {
          transitions.renderSlideBackground(
              scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
              scenario.getBackground(state.getCurrentBackgroundId()),
              state.getTransitionProgress(), width, height, false, this::resolveBackgroundResolverPath);
          handledTransitionBackground = true;
        }
        case WIPE -> {
          transitions.renderWipeBackground(
              scenario.getBackground(state.getPreviousBackgroundIdDuringTransition()),
              scenario.getBackground(state.getCurrentBackgroundId()),
              state.getTransitionProgress(), width, height, this::resolveBackgroundResolverPath);
          handledTransitionBackground = true;
        }
        default -> {
        }
      }
    }
    if (!handledTransitionBackground) {
      VnBackground prevBg = state.getPreviousBackgroundId() != null
          ? scenario.getBackground(state.getPreviousBackgroundId()) : null;
      if (prevBg != null) {
        renderBackground(prevBg, null, width, height);
      }
      VnBackground bg = state.getCurrentBackgroundId() != null
          ? scenario.getBackground(state.getCurrentBackgroundId()) : null;
      if (bg != null || activeStage != null) {
        renderBackground(bg, activeStage, width, height);
      }
    } else if (activeStage != null) {
      renderBackground(null, activeStage, width, height);
    }

    if (state.getActiveTransition() != null) {
      transitions.renderTransitionOverlay(state, width, height);
    }

    List<VnCharacterCompositor.CharacterRenderEntry> orderedCharacters = characters.orderedCharacterEntries(state);
    VnAudioVisualizerRenderer.AudioVisualizerSettings visualizerSettings = resolveAudioVisualizerSettings();
    characters.beginFrame();
    stageLighting.beginFrame();
    try {
      renderLayeredScene(orderedCharacters, state, scenario, activeStage, width, height, visualizerSettings);
    } finally {
      characters.endFrame();
      for (int i = 0; i < characters.characterLayerDrawCount(); i++) drawCallStats.incrementCharacterLayer();
      for (int i = 0; i < stageLighting.stageLightingRecomposeCount(); i++) drawCallStats.incrementStageLightingRecomposite();
    }
    stageLighting.renderStageLightOverlays(activeStage, width, height, VnStagePreset.LightLayer.FOREGROUND);

    VnNode currentNode = state.getCurrentNode();
    DialogueLine displayedDialogue = displayedDialogue(state);
    syncDialogueFade(state, displayedDialogue);
    if (currentNode != null && !state.isUiHidden()) {
      if (currentNode.getType() == VnNodeType.DIALOGUE && currentNode.getDialogue() != null) {
        String scenarioId = scenario == null ? "" : String.valueOf(scenario.getId());
        String nodeKey = scenarioId + ":" + state.getCurrentNodeIndex() + ":" + currentNode.getSourceLine();
        if (!nodeKey.equals(lastTtsNodeId) && textToSpeechEnabled && tts.isAvailable()) {
          lastTtsNodeId = nodeKey;
          String spokenText = resolveRuntimeText(currentNode.getDialogue().getText());
          String speaker = resolveRuntimeText(currentNode.getDialogue().getSpeakerName());
          if (speaker != null && !speaker.isBlank()) {
            spokenText = speaker + ". " + spokenText;
          }
          tts.speak(spokenText, Locale.getDefault());
        }
      }
      switch (currentNode.getType()) {
        case DIALOGUE -> renderDialogueWithFade(displayedDialogue, state, width, height, -1);
        case CHOICE -> {
          if (state.getDialoguePresentationMode() == DialoguePresentationMode.NVL) {
            dialogue.renderNvlHistory(state, uiLayout, width, height, dialogueRenderSettings());
          }
          renderChoicesInternal(currentNode.getChoices(), width, height, -1);
        }
        case END -> renderEnd(width, height);
        default -> {
        }
      }
      if (currentNode.getType() != VnNodeType.DIALOGUE
          && currentNode.getType() != VnNodeType.CHOICE
          && currentNode.getType() != VnNodeType.END) {
        renderDialogueWithFade(displayedDialogue, state, width, height, -1);
      }
    }
    if ((displayedDialogue == null || state.isUiHidden())
        && dialogueFadeLine != null && dialogueFadeAlpha > 0.001) {
      renderDialogueWithFade(dialogueFadeLine, state, width, height, -1);
    }

    choicesOverlays.renderOverlayScreens(state, width, height, null, nameFont, dialogueFont, choiceFont);
    choicesOverlays.renderModeIndicators(state, width, height, nameFont);

    renderHudToast(state, width, height);

    if (shaking) {
      blitter.pop();
    }
    transitions.renderFlashOverlay(state, width, height);
  }

  /** Splits a {@code |}-delimited layered sprite path spec into its component layer paths. */
  public static List<String> parseLayerPaths(String imagePathSpec) {
    return VnCharacterCompositor.parseLayerPaths(imagePathSpec);
  }

  /** Chooses the dialogue line rendered over the current non-dialogue action. */
  static @Nullable DialogueLine displayedDialogue(@Nullable VnState state) {
    if (state == null) return null;
    VnNode currentNode = state.getCurrentNode();
    return currentNode != null && currentNode.getType() == VnNodeType.DIALOGUE
        ? currentNode.getDialogue()
        : state.getRetainedDialogue();
  }

  /** Render with mouse hover support for choices/buttons. */
  public void render(VnState state, VnScenario scenario, double width, double height, double mouseX, double mouseY) {
    this.currentState = state;
    render(state, scenario, width, height);

    VnNode currentNode = state.getCurrentNode();
    if (currentNode != null && !state.isUiHidden()) {
      if (currentNode.getType() == VnNodeType.CHOICE) {
        int hoverIndex = getHoveredChoiceIndex(currentNode.getChoices(), width, height, mouseX, mouseY);
        if (state.getDialoguePresentationMode() == DialoguePresentationMode.NVL) {
          dialogue.renderNvlHistory(state, uiLayout, width, height, dialogueRenderSettings());
        }
        renderChoicesInternal(currentNode.getChoices(), width, height, hoverIndex);
      } else if (currentNode.getType() == VnNodeType.DIALOGUE) {
        int hoverButton = getHoveredTextBoxButtonIndexInternal(state, width, height, mouseX, mouseY);
        if (dialogueFadeAlpha >= 0.999) {
          renderDialogue(currentNode.getDialogue(), state, width, height, hoverButton);
        }
      }
    }
    choicesOverlays.renderOverlayScreens(
        state, width, height, getHoveredOverlayButton(state, width, height, mouseX, mouseY),
        nameFont, dialogueFont, choiceFont);
  }

  public void renderFrozen(VnState state, VnScenario scenario, double width, double height) {
    boolean previous = freezeTransientEffects;
    freezeTransientEffects = true;
    try {
      render(state, scenario, width, height);
    } finally {
      freezeTransientEffects = previous;
    }
  }

  public void updateAnimation(long deltaMs) {
    animationTime += deltaMs;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Background rendering
  // ─────────────────────────────────────────────────────────────────────────

  private void renderBackground(@Nullable VnBackground background, @Nullable VnStagePreset stage, double width, double height) {
    String backgroundPath = resolveBackgroundPath(background, stage);
    if (backgroundPath == null || backgroundPath.isBlank()) return;
    drawCallStats.incrementOther();
    if (stage != null) {
      stageLighting.drawStageBackground(backgroundPath, width, height, stage);
      stageLighting.applyStageBackgroundFallbackOverlay(stage, width, height);
      stageLighting.renderStageLightOverlays(stage, width, height, VnStagePreset.LightLayer.BACKGROUND);
    } else {
      blitter.drawImage(backgroundPath, 0, 0, width, height);
    }
  }

  private @Nullable String resolveBackgroundPath(@Nullable VnBackground background, @Nullable VnStagePreset stage) {
    if (stage != null && stage.getBackgroundTag() != null && !stage.getBackgroundTag().isBlank()) {
      return stage.getBackgroundTag();
    }
    return background == null ? null : background.getImagePath();
  }

  /** {@link VnTransitionRenderer.BackgroundPathResolver} implementation: backgrounds resolve to their configured image path directly, letting {@link Blitter2D#drawImage} do its own resolution/caching. */
  private @Nullable String resolveBackgroundResolverPath(@Nullable VnBackground background) {
    return background == null ? null : background.getImagePath();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Layered scene (characters + audio visualizer + particles)
  // ─────────────────────────────────────────────────────────────────────────

  private record LayeredSceneDraw(int z, int order, Runnable action) {}

  private void renderLayeredScene(
      List<VnCharacterCompositor.CharacterRenderEntry> orderedCharacters,
      VnState state,
      VnScenario scenario,
      @Nullable VnStagePreset stage,
      double width,
      double height,
      VnAudioVisualizerRenderer.AudioVisualizerSettings visualizerSettings) {
    List<LayeredSceneDraw> draws = new ArrayList<>();
    if (orderedCharacters != null) {
      for (int i = 0; i < orderedCharacters.size(); i++) {
        VnCharacterCompositor.CharacterRenderEntry entry = orderedCharacters.get(i);
        VnState.CharacterSlot slot = entry.slot();
        int z = slot != null ? slot.getLayerOrder() : 0;
        int order = i;
        draws.add(new LayeredSceneDraw(z, order,
            // VnCharacterCompositor.renderCharacterEntry's `stage` parameter is unannotated but its
            // body null-checks it throughout (`stage == null || stage.getLights().isEmpty()`); a
            // null active stage is the normal "no stage preset" case, not a bug.
            () -> renderCharacterEntrySafely(entry, state, scenario, stage, width, height)));
      }
    }

    draws.add(new LayeredSceneDraw(visualizerSettings.zIndex(), 10_000,
        () -> audioVisualizer.render(width, height, visualizerSettings, audioFacade, this::textBoxTopLeftGeometry)));

    if (prepareParticles(state, width, height)) {
      draws.add(new LayeredSceneDraw(lastParticleLayer, 20_000, () -> renderParticles(width, height)));
    }

    draws.sort(java.util.Comparator
        .comparingInt((LayeredSceneDraw item) -> item.z())
        .thenComparingInt(LayeredSceneDraw::order));

    for (LayeredSceneDraw draw : draws) {
      draw.action().run();
    }
  }

  @SuppressWarnings("NullAway")
  private void renderCharacterEntrySafely(
      VnCharacterCompositor.CharacterRenderEntry entry, VnState state, VnScenario scenario,
      @Nullable VnStagePreset stage, double width, double height) {
    characters.renderCharacterEntry(entry, state, scenario, stage, width, height);
  }

  private double[] textBoxTopLeftGeometry(double width, double height) {
    double textBoxX = clamp(width * uiLayout.textBoxX(), 0, width);
    double textBoxY = clamp(height * uiLayout.textBoxY(), 0, height);
    double maxBoxWidth = Math.max(1, width - textBoxX);
    double maxBoxHeight = Math.max(1, height - textBoxY);
    double textBoxWidth = clamp(width * uiLayout.textBoxWidth(), 1, maxBoxWidth);
    double textBoxHeight = clamp(height * uiLayout.textBoxHeight(), 1, maxBoxHeight);
    return new double[] {textBoxX, textBoxY, textBoxWidth, textBoxHeight};
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Particles
  // ─────────────────────────────────────────────────────────────────────────

  private boolean prepareParticles(VnState state, double width, double height) {
    VnParticleCommand cmd = state == null ? null : state.getActiveParticleCommand();
    boolean sizeChanged = Math.abs(width - particleConfigWidth) > 0.5
        || Math.abs(height - particleConfigHeight) > 0.5;

    if (cmd == null && renderedParticleCommand == null && particleEmitter.getParticleCount() <= 0) {
      return false;
    }

    if (cmd != null && (cmd != renderedParticleCommand || sizeChanged)) {
      VnParticlePresetLibrary.apply(particleEmitter, cmd, width, height);
      if (cmd.getPrewarmMs() > 0L && particleEmitter.getParticleCount() == 0) {
        long remaining = cmd.getPrewarmMs();
        while (remaining > 0L) {
          long step = Math.min(16L, remaining);
          particleEmitter.update(step);
          remaining -= step;
        }
      }
      renderedParticleCommand = cmd;
      lastParticleLayer = cmd.getLayer();
      particleConfigWidth = width;
      particleConfigHeight = height;
    } else if (cmd == null && renderedParticleCommand != null) {
      particleEmitter.setEmitting(false);
      renderedParticleCommand = null;
    }

    long now = System.nanoTime();
    if (freezeTransientEffects) {
      particleLastFrameNanos = now;
      return cmd != null || particleEmitter.getParticleCount() > 0;
    }
    long deltaMs = 16L;
    if (particleLastFrameNanos > 0L) {
      deltaMs = Math.max(0L, Math.min(250L, (now - particleLastFrameNanos) / 1_000_000L));
    }
    particleLastFrameNanos = now;
    particleEmitter.update(deltaMs);

    return cmd != null || particleEmitter.getParticleCount() > 0;
  }

  private void renderParticles(double width, double height) {
    if (particleEmitter.getParticleCount() <= 0 && renderedParticleCommand == null) return;
    drawCallStats.incrementOther();
    blitter.push();
    blitter.translate(particleEmitter.getX(), particleEmitter.getY());
    if (particleEmitter.getRotationDeg() != 0.0) {
      blitter.rotateDeg(particleEmitter.getRotationDeg());
    }
    if (particleEmitter.getScaleX() != 1.0 || particleEmitter.getScaleY() != 1.0) {
      blitter.scale(particleEmitter.getScaleX(), particleEmitter.getScaleY());
    }
    particleEmitter.render(blitter);
    blitter.pop();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Dialogue dispatch
  // ─────────────────────────────────────────────────────────────────────────

  private VnDialogueRenderer.RenderSettings dialogueRenderSettings() {
    return new VnDialogueRenderer.RenderSettings(
        nameFont, dialogueFont, choiceFont,
        shouldUseDefaultDialogueStyle(currentState),
        appliedUiFontScale,
        shouldUseTextBoxAsset(currentState), textBoxImagePath, narrationTextBoxImagePath, textBoxFillColor,
        textBoxAssetOverlayOpacity, textBoxBoundsPolygon,
        nameBoxImagePath, nameBoxFillColor, nameTextFillColor, nameTextXAlign, nameBoxRenderOpacity,
        nameBoxBoundsPolygon,
        dialogueTextFillColor, dialogueTextXAlign, dialogueTextBoundsPolygon,
        continueIndicatorImagePath, 0.0, 0.0,
        nvlPanelImagePath, nvlPanelFillColor, nvlPanelOpacity, nvlSpeakerTextFillColor, nvlTextFillColor,
        bubbleImagePath, bubbleFillColor, bubbleOpacity, bubbleBorderFillColor, 2.0, 20.0,
        bubbleSpeakerTextFillColor, bubbleTextFillColor,
        shouldRenderTextBoxButtons(currentState), textBoxButtons);
  }

  private void renderDialogue(DialogueLine dialogueLine, VnState state, double width, double height, int hoveredButtonIndex) {
    if (dialogueLine == null) return;
    DialoguePresentationMode mode = state == null ? DialoguePresentationMode.STANDARD : state.getDialoguePresentationMode();
    VnDialogueRenderer.RenderSettings settings = dialogueRenderSettings();
    switch (mode) {
      case NVL -> dialogue.renderNvlDialogue(dialogueLine, state, uiLayout, width, height, settings);
      case BUBBLE -> {
        // Bubble anchor placement is explicitly out of VnDialogueRenderer's scope (see its
        // VnCharacterScale Javadoc): resolving it fully requires the character-position/visual/
        // displacement chain VnCharacterCompositor owns, which has no public accessor for a
        // single character's screen anchor point. VnCharacterScale.none() (screen-center anchor,
        // unscaled) is used as a safe, documented fallback rather than reimplementing that
        // resolution chain here.
        dialogue.renderBubbleDialogue(
            dialogueLine, state, VnDialogueRenderer.VnCharacterScale.none(), uiLayout, width, height, settings);
      }
      default -> dialogue.renderStandardDialogue(dialogueLine, state, uiLayout, width, height, hoveredButtonIndex, settings);
    }
  }

  private void renderDialogueWithFade(@Nullable DialogueLine dialogueLine, VnState state, double width, double height, int hoveredButtonIndex) {
    if (dialogueLine == null || dialogueFadeAlpha <= 0.001) return;
    blitter.push();
    blitter.setGlobalAlpha(clamp(dialogueFadeAlpha, 0.0, 1.0));
    renderDialogue(dialogueLine, state, width, height, hoveredButtonIndex);
    blitter.pop();
  }

  private void syncDialogueFade(VnState state, @Nullable DialogueLine displayedDialogue) {
    boolean requestedVisible = state != null && !state.isUiHidden() && displayedDialogue != null;

    if (state != dialogueFadeState) {
      dialogueFadeState = state;
      dialogueFadeLine = requestedVisible ? displayedDialogue : null;
      dialogueFadeVisible = requestedVisible;
      dialogueFadeAlpha = 0.0;
      dialogueFadeFrom = dialogueFadeAlpha;
      dialogueFadeTarget = requestedVisible ? 1.0 : 0.0;
      dialogueFadeStartedAtNanos = System.nanoTime();
    } else if (requestedVisible != dialogueFadeVisible) {
      dialogueFadeVisible = requestedVisible;
      dialogueFadeFrom = dialogueFadeAlpha;
      dialogueFadeTarget = requestedVisible ? 1.0 : 0.0;
      dialogueFadeStartedAtNanos = System.nanoTime();
    }

    if (requestedVisible) {
      dialogueFadeLine = displayedDialogue;
    }
    if (freezeTransientEffects) {
      dialogueFadeFrom = dialogueFadeAlpha;
      dialogueFadeStartedAtNanos = System.nanoTime();
    } else {
      double durationMs = 500.0;
      Double configured = readDoubleVariable(state, VAR_DIALOGUE_FADE_MS);
      if (configured != null && Double.isFinite(configured)) {
        durationMs = clamp(configured, 0.0, 10_000.0);
      }
      if (durationMs <= 0.0) {
        dialogueFadeAlpha = dialogueFadeTarget;
      } else {
        double elapsedMs = Math.max(0.0, (System.nanoTime() - dialogueFadeStartedAtNanos) / 1_000_000.0);
        double progress = clamp(elapsedMs / durationMs, 0.0, 1.0);
        dialogueFadeAlpha = dialogueFadeFrom + (dialogueFadeTarget - dialogueFadeFrom) * progress;
      }
    }
    if (!requestedVisible && dialogueFadeAlpha <= 0.001) {
      dialogueFadeLine = null;
    }
  }

  private void renderEnd(double width, double height) {
    blitter.setFill(TEXT_COLOR[0], TEXT_COLOR[1], TEXT_COLOR[2], TEXT_COLOR[3]);
    blitter.setFont(nameFont.family(), 32, true);
    blitter.drawText("End", width / 2 - 30, height / 2, 32, true);
  }

  private void renderHudToast(VnState state, double width, double height) {
    long now = System.currentTimeMillis();
    if (state.getHudMessage() == null || now >= state.getHudMessageExpireAt()) return;
    VnFontSpec hudFont = new VnFontSpec(nameFont.family(), 16, true);
    String msg = state.getHudMessage();
    double lineHeight = Math.max(hudFont.size() * 1.25, blitter.measureTextMetrics(
        "Mg", hudFont.family(), hudFont.size(), hudFont.bold()).lineHeight() + 3.0);
    HudToastLayout.Layout toast = HudToastLayout.compute(
        msg, width, lineHeight,
        line -> blitter.measureTextMetrics(line, hudFont.family(), hudFont.size(), hudFont.bold()).width());
    blitter.setFill(0, 0, 0, 0.6);
    double boxW = toast.width();
    double boxH = toast.height();
    double bx = (width - boxW) / 2;
    double by = clamp(height * 0.1, 16.0, Math.max(16.0, height - boxH - 16.0));
    blitter.fillRect(bx, by, boxW, boxH);
    blitter.setFill(1, 1, 1, 1);
    double baseline = by + HudToastLayout.VERTICAL_PADDING
        + blitter.measureTextMetrics("Mg", hudFont.family(), hudFont.size(), hudFont.bold()).ascent();
    blitter.setFont(hudFont.family(), hudFont.size(), hudFont.bold());
    for (String line : toast.lines()) {
      blitter.drawText(line, bx + HudToastLayout.HORIZONTAL_PADDING, baseline, hudFont.size(), hudFont.bold());
      baseline += lineHeight;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Choices dispatch
  // ─────────────────────────────────────────────────────────────────────────

  private VnChoiceOverlayRenderer.ChoiceTheme choiceTheme() {
    return new VnChoiceOverlayRenderer.ChoiceTheme(
        choiceBgColor, choiceHoverColor, choiceDisabledColor,
        choiceTextColor, choiceHoverTextColor, choiceDisabledTextColor,
        choiceBorderColor, choiceHoverBorderColor, choiceDisabledBorderColor,
        choiceCornerRadius, choiceBorderWidth, choiceTextBaselineOffset, choiceTextXAlign,
        choiceButtonImagePath, choiceButtonHoverImagePath, choiceButtonDisabledImagePath,
        choiceButtonBoundsPolygon);
  }

  private void renderChoicesInternal(List<Choice> choices, double width, double height, int hoverIndex) {
    choicesOverlays.renderChoices(
        choices, uiLayout, choiceFont, choiceTheme(), width, height, hoverIndex,
        currentState == null ? null : currentState.getVariables());
  }

  public int getHoveredChoiceIndex(List<Choice> choices, double width, double height, double mouseX, double mouseY) {
    return choicesOverlays.getHoveredChoiceIndex(choices, uiLayout, choiceTheme(), width, height, mouseX, mouseY);
  }

  public @Nullable VnUiActionButtonSpec getHoveredTextBoxButton(VnState state, double width, double height, double mouseX, double mouseY) {
    int idx = getHoveredTextBoxButtonIndexInternal(state, width, height, mouseX, mouseY);
    if (idx < 0 || idx >= textBoxButtons.size()) return null;
    return textBoxButtons.get(idx);
  }

  private int getHoveredTextBoxButtonIndexInternal(VnState state, double width, double height, double mouseX, double mouseY) {
    if (state == null || state.isUiHidden()) return -1;
    if (state.getDialoguePresentationMode() != DialoguePresentationMode.STANDARD) return -1;
    VnNode currentNode = state.getCurrentNode();
    if (currentNode == null || currentNode.getType() != VnNodeType.DIALOGUE) return -1;
    return dialogue.getHoveredTextBoxButtonIndex(state, uiLayout, width, height, mouseX, mouseY, dialogueRenderSettings());
  }

  public @Nullable VnOverlayButtonSpec getHoveredOverlayButton(VnState state, double width, double height, double mouseX, double mouseY) {
    return choicesOverlays.getHoveredOverlayButton(state, width, height, mouseX, mouseY);
  }

  public int renderErrorOverlay(@Nullable VnErrorOverlay error, double width, double height, double mouseX, double mouseY) {
    return choicesOverlays.renderErrorOverlay(error, width, height, mouseX, mouseY);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Cache / disposal
  // ─────────────────────────────────────────────────────────────────────────

  public void clearCache() {
    characters.clearCache();
    stageLighting.clearCache();
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    clearCache();
    timelineAccessor = null;
    currentState = null;
    projectRoot = null;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Theme application
  // ─────────────────────────────────────────────────────────────────────────

  private void applyUiStyle(VnUiStyleSpec style) {
    VnUiStyleSpec resolved = style == null ? VnUiStyleSpec.defaults() : style;
    textBoxImagePath = blank(resolved.textBoxAssetPath());
    narrationTextBoxImagePath = blank(resolved.textBoxNarrationAssetPath());
    nameBoxImagePath = blank(resolved.nameBoxAssetPath());
    nvlPanelImagePath = blank(resolved.nvlPanelAssetPath());
    bubbleImagePath = blank(resolved.bubbleAssetPath());
    textBoxFillColor = parseColor(resolved.textBoxColor(), TEXTBOX_COLOR);
    nameBoxFillColor = parseColor(resolved.nameBoxColor(), NAME_BOX_COLOR);
    nameTextFillColor = parseColor(resolved.nameTextColor(), DEFAULT_NAME_TEXT_COLOR);
    dialogueTextFillColor = parseColor(resolved.dialogueTextColor(), TEXT_COLOR);
    nvlPanelFillColor = parseColor(resolved.nvlPanelColor(), parseHex("#08111ACC"));
    nvlSpeakerTextFillColor = parseColor(resolved.nvlSpeakerTextColor(), parseHex("#F7D89A"));
    nvlTextFillColor = parseColor(resolved.nvlTextColor(), dialogueTextFillColor);
    bubbleFillColor = parseColor(resolved.bubbleColor(), parseHex("#152238EE"));
    bubbleBorderFillColor = parseColor(resolved.bubbleBorderColor(), parseHex("#A9BCD9"));
    bubbleSpeakerTextFillColor = parseColor(resolved.bubbleSpeakerTextColor(), parseHex("#FFD78A"));
    bubbleTextFillColor = parseColor(resolved.bubbleTextColor(), dialogueTextFillColor);
    textBoxAssetOverlayOpacity = clamp(resolved.textBoxOpacity() == null ? 0.88 : resolved.textBoxOpacity(), 0.0, 1.0);
    nvlPanelOpacity = clamp(resolved.nvlPanelOpacity() == null ? 0.84 : resolved.nvlPanelOpacity(), 0.0, 1.0);
    bubbleOpacity = clamp(resolved.bubbleOpacity() == null ? 0.96 : resolved.bubbleOpacity(), 0.0, 1.0);
    textBoxBoundsPolygon = parseBoundsPoints(resolved.textBoxBoundsPoints());
    nameBoxBoundsPolygon = parseBoundsPoints(resolved.nameBoxBoundsPoints());
    dialogueTextBoundsPolygon = parseBoundsPoints(resolved.dialogueTextBoundsPoints());

    choiceButtonImagePath = blank(resolved.choiceButtonAssetPath());
    choiceButtonHoverImagePath = blank(firstNonBlank(
        resolved.choiceButtonHoverAssetPath(), resolved.choiceButtonSelectedAssetPath()));
    choiceButtonDisabledImagePath = blank(resolved.choiceButtonDisabledAssetPath());
    choiceButtonBoundsPolygon = parseBoundsPoints(resolved.choiceButtonBoundsPoints());

    choiceBgColor = parseColor(resolved.choiceBackgroundColor(), CHOICE_BG_COLOR);
    choiceHoverColor = parseColor(
        firstNonBlank(resolved.choiceHoverColor(), resolved.choiceSelectedColor()), CHOICE_HOVER_COLOR);
    choiceDisabledColor = parseColor(resolved.choiceDisabledColor(), CHOICE_DISABLED_COLOR);
    choiceTextColor = parseColor(resolved.choiceTextColor(), TEXT_COLOR);
    choiceHoverTextColor = parseColor(
        firstNonBlank(resolved.choiceHoverTextColor(), resolved.choiceSelectedTextColor()), choiceTextColor);
    choiceDisabledTextColor = parseColor(resolved.choiceDisabledTextColor(), TEXT_COLOR_DISABLED);
    choiceBorderColor = parseColor(resolved.choiceBorderColor(), TEXT_COLOR);
    choiceHoverBorderColor = parseColor(
        firstNonBlank(resolved.choiceHoverBorderColor(), resolved.choiceSelectedBorderColor()), choiceBorderColor);
    choiceDisabledBorderColor = parseColor(resolved.choiceDisabledBorderColor(), CHOICE_DISABLED_BORDER_COLOR);
    choiceBorderWidth = clamp(resolved.choiceBorderWidth(), 0.0, 12.0);
    choiceCornerRadius = clamp(resolved.choiceCornerRadius(), 0.0, 96.0);
    choiceTextBaselineOffset = clamp(resolved.choiceTextBaselineOffset(), -120.0, 120.0);
    nameTextXAlign = clamp(resolved.nameTextXAlign() == null ? 0.0 : resolved.nameTextXAlign(), 0.0, 1.0);
    dialogueTextXAlign = clamp(resolved.dialogueTextXAlign() == null ? 0.0 : resolved.dialogueTextXAlign(), 0.0, 1.0);
    choiceTextXAlign = clamp(resolved.choiceTextXAlign() == null ? 0.0 : resolved.choiceTextXAlign(), 0.0, 1.0);

    String nameFontFamily = resolved.nameTextFontFamily() != null ? resolved.nameTextFontFamily() : DEFAULT_FONT_FAMILY;
    int nameFontSize = resolved.nameTextFontSize() != null ? resolved.nameTextFontSize() : DEFAULT_NAME_FONT_SIZE;
    this.nameFont = new VnFontSpec(nameFontFamily, nameFontSize, true);

    String dialogueFontFamily = resolved.dialogueTextFontFamily() != null ? resolved.dialogueTextFontFamily() : DEFAULT_FONT_FAMILY;
    int dialogueFontSize = resolved.dialogueTextFontSize() != null ? resolved.dialogueTextFontSize() : DEFAULT_DIALOGUE_FONT_SIZE;
    this.dialogueFont = new VnFontSpec(dialogueFontFamily, dialogueFontSize, false);

    String choiceFontFamily = resolved.choiceFontFamily() != null ? resolved.choiceFontFamily() : DEFAULT_FONT_FAMILY;
    int choiceFontSize = resolved.choiceFontSize() != null ? resolved.choiceFontSize() : DEFAULT_CHOICE_FONT_SIZE;
    this.choiceFont = new VnFontSpec(choiceFontFamily, choiceFontSize, false);

    nameBoxRenderOpacity = clamp(resolved.nameBoxOpacity() == null ? 1.0 : resolved.nameBoxOpacity(), 0.0, 1.0);

    styleCharacterHeightFactor = clamp(
        resolved.characterHeightFactor() == null ? DEFAULT_CHARACTER_HEIGHT_FACTOR : resolved.characterHeightFactor(),
        0.1, 3.0);
    styleCharacterBaselineY = clamp(
        resolved.characterBaselineY() == null ? DEFAULT_CHARACTER_BASELINE_Y : resolved.characterBaselineY(),
        -0.5, 2.0);
    characterHeightFactor = styleCharacterHeightFactor;
    characterBaselineY = styleCharacterBaselineY;
    characters.setCharacterFraming(characterHeightFactor, characterBaselineY);

    if (accessibilityTheme != null && accessibilityTheme.isActive()) {
      applyAccessibilityThemeOverrides(accessibilityTheme);
    }
    applyUiFontScale();
  }

  private void syncAccessibilitySettings(VnState state) {
    VnSettings settings = state == null ? null : state.getSettings();
    String requestedTheme = normalizeAccessibilityThemeName(settings == null ? "none" : settings.getAccessibilityTheme());
    double requestedScale = settings == null ? 1.0 : settings.getUiFontScale();
    boolean requestedTts = settings != null && settings.isTextToSpeechEnabled();

    boolean themeChanged = !appliedAccessibilityThemeName.equals(requestedTheme);
    boolean scaleChanged = Math.abs(appliedUiFontScale - requestedScale) > 0.0001;
    if (themeChanged || scaleChanged) {
      accessibilityTheme = AccessibilityThemeLoader.load(requestedTheme);
      appliedAccessibilityThemeName = requestedTheme;
      appliedUiFontScale = requestedScale;
      applyUiStyle(uiStyle);
    }
    if (textToSpeechEnabled && !requestedTts) {
      tts.stop();
      lastTtsNodeId = null;
    }
    textToSpeechEnabled = requestedTts;
  }

  private void applyUiFontScale() {
    if (Math.abs(appliedUiFontScale - 1.0) < 0.0001) return;
    nameFont = new VnFontSpec(nameFont.family(), nameFont.size() * appliedUiFontScale, nameFont.bold());
    dialogueFont = new VnFontSpec(dialogueFont.family(), dialogueFont.size() * appliedUiFontScale, dialogueFont.bold());
    choiceFont = new VnFontSpec(choiceFont.family(), choiceFont.size() * appliedUiFontScale, choiceFont.bold());
  }

  private void applyAccessibilityThemeOverrides(AccessibilityThemeLoader theme) {
    dialogueTextFillColor = parseColor(theme.dialogueTextColor(null), dialogueTextFillColor);
    textBoxFillColor = parseColor(theme.dialogueTextboxColor(null), textBoxFillColor);
    textBoxAssetOverlayOpacity = theme.dialogueTextboxOpacity(textBoxAssetOverlayOpacity);
    nameBoxFillColor = parseColor(theme.nameBoxColor(null), nameBoxFillColor);
    nameBoxRenderOpacity = theme.nameBoxOpacity(nameBoxRenderOpacity);
    nameTextFillColor = parseColor(theme.nameTextColor(null), nameTextFillColor);
    choiceBgColor = parseColor(theme.choiceBackgroundColor(null), choiceBgColor);
    choiceTextColor = parseColor(theme.choiceTextColor(null), choiceTextColor);
    choiceHoverColor = parseColor(theme.choiceHoverColor(null), choiceHoverColor);
    choiceHoverTextColor = parseColor(theme.choiceHoverTextColor(null), choiceHoverTextColor);
    choiceBorderColor = parseColor(theme.choiceBorderColor(null), choiceBorderColor);
    choiceHoverBorderColor = parseColor(theme.choiceHoverBorderColor(null), choiceHoverBorderColor);
    choiceBorderWidth = theme.choiceBorderWidth(choiceBorderWidth);
    choiceCornerRadius = theme.choiceCornerRadius(choiceCornerRadius);
    nvlPanelFillColor = parseColor(theme.nvlPanelColor(null), nvlPanelFillColor);
    nvlPanelOpacity = theme.nvlPanelOpacity(nvlPanelOpacity);
    nvlTextFillColor = parseColor(theme.nvlTextColor(null), nvlTextFillColor);
    nvlSpeakerTextFillColor = parseColor(theme.nvlSpeakerTextColor(null), nvlSpeakerTextFillColor);
    bubbleFillColor = parseColor(theme.bubbleColor(null), bubbleFillColor);
    bubbleOpacity = theme.bubbleOpacity(bubbleOpacity);
    bubbleTextFillColor = parseColor(theme.bubbleTextColor(null), bubbleTextFillColor);
    bubbleSpeakerTextFillColor = parseColor(theme.bubbleSpeakerTextColor(null), bubbleSpeakerTextFillColor);
    bubbleBorderFillColor = parseColor(theme.bubbleBorderColor(null), bubbleBorderFillColor);

    String themeNameFamily = theme.nameTextFontFamily(null);
    if (themeNameFamily != null) {
      nameFont = new VnFontSpec(themeNameFamily, nameFont.size(), true);
    }
    String themeDialogueFamily = theme.dialogueTextFontFamily(null);
    if (themeDialogueFamily != null) {
      dialogueFont = new VnFontSpec(themeDialogueFamily, dialogueFont.size(), false);
    }
    String themeChoiceFamily = theme.choiceFontFamily(null);
    if (themeChoiceFamily != null) {
      choiceFont = new VnFontSpec(themeChoiceFamily, choiceFont.size(), false);
    }
  }

  private void applyRuntimeCharacterFramingOverrides(VnState state) {
    if (state == null) {
      characterHeightFactor = styleCharacterHeightFactor;
      characterBaselineY = styleCharacterBaselineY;
      characters.setCharacterFraming(characterHeightFactor, characterBaselineY);
      return;
    }
    Double heightOverride = readDoubleVariable(state, VAR_CHARACTER_HEIGHT_FACTOR);
    Double baselineOverride = readDoubleVariable(state, VAR_CHARACTER_BASELINE_Y);
    characterHeightFactor = clamp(heightOverride == null ? styleCharacterHeightFactor : heightOverride, 0.1, 3.0);
    characterBaselineY = clamp(baselineOverride == null ? styleCharacterBaselineY : baselineOverride, -0.5, 2.0);
    characters.setCharacterFraming(characterHeightFactor, characterBaselineY);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Variable readers / dialogue-ui toggles
  // ─────────────────────────────────────────────────────────────────────────

  private @Nullable Double readDoubleVariable(@Nullable VnState state, String key) {
    if (state == null || key.isBlank()) return null;
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

  private @Nullable String readStringVariable(@Nullable VnState state, String key) {
    if (state == null || key.isBlank()) return null;
    Object value = state.getVariables().get(key);
    if (value == null) return null;
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }

  private @Nullable Boolean readBooleanVariable(@Nullable VnState state, String key) {
    if (state == null || key.isBlank()) return null;
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

  private boolean shouldUseDefaultDialogueUi(@Nullable VnState state) {
    return disablesCustomDialogueUi(readStringVariable(state, VAR_DIALOGUE_UI));
  }

  private boolean shouldUseDefaultDialogueStyle(@Nullable VnState state) {
    return shouldUseDefaultDialogueUi(state) || disablesCustomDialogueUi(readStringVariable(state, VAR_DIALOGUE_STYLE));
  }

  private boolean shouldUseTextBoxAsset(@Nullable VnState state) {
    if (shouldUseDefaultDialogueUi(state)) return false;
    Boolean enabled = readBooleanVariable(state, VAR_TEXT_BOX_ASSET_ENABLED);
    if (enabled != null) return enabled;
    String mode = readStringVariable(state, VAR_TEXT_BOX_ASSET);
    if (mode == null) return true;
    String normalized = mode.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "default", "builtin", "built-in", "solid", "fill", "none", "off", "false", "0", "no" -> false;
      default -> true;
    };
  }

  private boolean shouldRenderTextBoxButtons(@Nullable VnState state) {
    if (shouldUseDefaultDialogueUi(state)) return false;
    Boolean enabled = readBooleanVariable(state, VAR_TEXT_BOX_BUTTONS_ENABLED);
    if (enabled != null) return enabled;
    String mode = readStringVariable(state, VAR_TEXT_BOX_BUTTONS);
    if (mode == null) return true;
    return !disablesCustomDialogueUi(mode);
  }

  private boolean disablesCustomDialogueUi(@Nullable String raw) {
    if (raw == null || raw.isBlank()) return false;
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "default", "builtin", "built-in", "solid", "fill", "plain", "none", "off", "false", "0", "no" -> true;
      default -> false;
    };
  }

  private static String normalizeAccessibilityThemeName(@Nullable String themeName) {
    return themeName == null || themeName.isBlank() ? "none" : themeName.trim().toLowerCase(Locale.ROOT);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Stage preset resolution
  // ─────────────────────────────────────────────────────────────────────────

  private @Nullable VnStagePreset resolveActiveStagePreset(@Nullable VnState state, @Nullable VnScenario scenario) {
    if (state == null || scenario == null) return null;
    String stageId = state.getActiveStagePresetId();
    if (stageId == null || stageId.isBlank()) return null;
    return scenario.getStagePreset(stageId);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Audio visualizer settings
  // ─────────────────────────────────────────────────────────────────────────

  private boolean isAudioVisualizerEnabled() {
    return currentState != null
        && VnAudioVisualizerConfig.isTruthy(currentState.getVariables().get(VnAudioVisualizerConfig.VAR_ENABLED));
  }

  private int resolveAudioVisualizerBarCount() {
    Double override = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_BARS);
    if (override == null) return VnAudioVisualizerConfig.DEFAULT_BARS;
    return VnAudioVisualizerConfig.clampBars((int) Math.round(override));
  }

  private VnAudioVisualizerRenderer.AudioVisualizerSettings resolveAudioVisualizerSettings() {
    String style = VnAudioVisualizerConfig.normalizeStyle(readStringVariable(currentState, VnAudioVisualizerConfig.VAR_STYLE));
    String rawColorToken = readStringVariable(currentState, VnAudioVisualizerConfig.VAR_COLOR);
    String colorToken = rawColorToken == null ? "auto" : rawColorToken;
    String rawAccentToken = readStringVariable(currentState, VnAudioVisualizerConfig.VAR_ACCENT);
    String accentToken = rawAccentToken == null ? "auto" : rawAccentToken;
    Double alphaValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_ALPHA);
    Double heightValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_HEIGHT);
    Double zValue = readDoubleVariable(currentState, VnAudioVisualizerConfig.VAR_Z);
    Boolean glowValue = readBooleanVariable(currentState, VnAudioVisualizerConfig.VAR_GLOW);
    return new VnAudioVisualizerRenderer.AudioVisualizerSettings(
        isAudioVisualizerEnabled(),
        resolveAudioVisualizerBarCount(),
        style, colorToken, accentToken,
        alphaValue == null ? VnAudioVisualizerConfig.DEFAULT_ALPHA : VnAudioVisualizerConfig.clampAlpha(alphaValue),
        glowValue == null || glowValue,
        heightValue == null ? VnAudioVisualizerConfig.DEFAULT_HEIGHT : VnAudioVisualizerConfig.clampHeight(heightValue),
        zValue == null ? VnAudioVisualizerConfig.DEFAULT_Z : (int) Math.round(zValue));
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Small shared helpers
  // ─────────────────────────────────────────────────────────────────────────

  private @Nullable String resolveRuntimeText(@Nullable String text) {
    if (text == null) return "";
    String translated = Localization.translateText(text);
    if (currentState == null) return translated;
    return VnVariableInterpolator.interpolate(translated, currentState.getVariables());
  }

  private static double clamp(double value, double min, double max) {
    if (Double.isNaN(value) || Double.isInfinite(value)) return min;
    if (value < min) return min;
    if (value > max) return max;
    return value;
  }

  private static @Nullable String blank(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return null;
  }

  private static double[] parseColor(@Nullable String raw, double[] fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    double[] parsed = tryParseHex(raw);
    return parsed != null ? parsed : fallback;
  }

  private static double @Nullable [] tryParseHex(String raw) {
    String hex = raw.trim();
    if (hex.startsWith("#")) hex = hex.substring(1);
    try {
      if (hex.length() == 6) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new double[] {r / 255.0, g / 255.0, b / 255.0, 1.0};
      }
      if (hex.length() == 8) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        int a = Integer.parseInt(hex.substring(6, 8), 16);
        return new double[] {r / 255.0, g / 255.0, b / 255.0, a / 255.0};
      }
    } catch (Exception ignored) {
      // reason: non-critical operation; caller falls back to its default color
    }
    return null;
  }

  private static double[] parseHex(String hex) {
    double[] parsed = tryParseHex(hex);
    return parsed != null ? parsed : new double[] {0, 0, 0, 1};
  }

  private static List<BoundsPointCodec.Point> parseBoundsPoints(@Nullable String raw) {
    List<BoundsPointCodec.Point> parsed = BoundsPointCodec.parse(raw);
    return parsed.size() >= 3 ? parsed : List.of();
  }
}
