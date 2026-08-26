package com.jvn.scenerender.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.ui.BoundsPointCodec;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.text.TextEffect;
import com.jvn.core.vn.text.TextParser;
import com.jvn.core.vn.text.TextSpan;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import org.jspecify.annotations.Nullable;

/**
 * Dialogue/text-rendering collaborator ported from the original monolithic {@code VnRenderer}
 * (JavaFX {@code GraphicsContext}-bound) onto the platform-agnostic {@link Blitter2D} drawing
 * abstraction. Covers the standard text-box, NVL, and speech-bubble dialogue presentation modes,
 * plus the shared styled-text layout/paint pipeline (inline markup effects, word wrap, continue
 * indicator) all three modes route through.
 *
 * <h2>Known port limitations</h2>
 * <ul>
 *   <li><b>Polygon-shaped clip regions are not applied.</b> The original JavaFX code clipped to an
 *   arbitrary polygon (via {@code gc.clip()} after building a non-rectangular path) whenever a
 *   theme configured a {@code textBoxBoundsPolygon}/{@code nameBoxBoundsPolygon}/dialogue-text or
 *   button bounds polygon. {@link Blitter2D#setClipRect} is rectangular-only and {@code Blitter2D}
 *   has no arbitrary-polygon clip primitive at all, so this port draws the image/text unclipped in
 *   that case instead of faking an ineffective clip. This is a known, minor visual-regression risk
 *   — it only affects projects with a configured non-rectangular text-box/name-box/choice-button
 *   bounds polygon — to be confirmed during a later task's visual verification. Polygon
 *   <em>stroking</em> (used for button outlines) is unaffected since {@link Blitter2D#strokePolygon}
 *   exists and is used as-is.</li>
 *   <li><b>Synthetic italic is always used for {@link TextEffect#ITALIC} spans.</b> The original
 *   used {@code ItalicFontSupport} to prefer a font family's native italic face (detected via
 *   JavaFX's installed-font metadata) and only fell back to a synthetic shear transform when no
 *   native italic face existed. {@link Blitter2D#setFont} has no posture/italic parameter at all —
 *   there is no way to request or draw a native italic face through this interface regardless of
 *   what a font-metadata probe might report — so native-italic detection has no equivalent here.
 *   This port always applies the synthetic shear for italic spans, which is a safe simplification
 *   (not a "worse guess"): even a correct native-italic detection couldn't be acted on given
 *   {@code Blitter2D}'s font API.</li>
 * </ul>
 */
final class VnDialogueRenderer {

  private static final String DEFAULT_FONT_FAMILY = "SansSerif";
  private static final int DEFAULT_NAME_FONT_SIZE = 18;
  private static final int DEFAULT_DIALOGUE_FONT_SIZE = 22;

  // TEXTBOX_COLOR = Color.rgb(12, 18, 32, 0.88); NAME_BOX_COLOR = Color.rgb(20, 32, 56, 0.56);
  // TEXT_COLOR = Color.web("#E8EDF6"); default-dialogue-style RGBA fallbacks, normalised.
  private static final double[] TEXTBOX_COLOR = {12.0 / 255, 18.0 / 255, 32.0 / 255, 0.88};
  private static final double[] NAME_BOX_COLOR = {20.0 / 255, 32.0 / 255, 56.0 / 255, 0.56};
  private static final double[] TEXT_COLOR = {0xE8 / 255.0, 0xED / 255.0, 0xF6 / 255.0, 1.0};
  private static final double[] DEFAULT_NAME_TEXT_COLOR = {1.0, 0xD7 / 255.0, 0x8A / 255.0, 1.0}; // #FFD78A

  /** Synthetic-italic shear factor, matching the original {@code ItalicFontSupport.SYNTHETIC_SHEAR}. */
  private static final double SYNTHETIC_ITALIC_SHEAR = -0.22;

  private final Blitter2D blitter;

  /** Test seam: a directly-configured text box asset path, used by {@link #renderStandardDialogueBackdropOnly}. */
  private @Nullable String textBoxAssetPath;

  VnDialogueRenderer(Blitter2D blitter) {
    this.blitter = blitter;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  RenderSettings — theme-derived fields VnRenderer previously held directly
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Bundles the theme-derived fields the original {@code VnRenderer} held as instance state and
   * read from {@code renderStandardDialogue}/{@code renderNvlDialogue}/{@code renderBubbleDialogue}
   * (and the collaborators those call). Colors are normalised RGBA quads ({@code [r,g,b,a]} each in
   * {@code [0,1]}); asset fields are classpath/filesystem path strings ({@code Blitter2D.drawImage}
   * resolves+caches them internally) or {@code null} when unset, matching each original
   * {@code Image}-typed field exactly.
   */
  record RenderSettings(
      VnFontSpec nameFont,
      VnFontSpec dialogueFont,
      VnFontSpec choiceFont,
      boolean defaultDialogueStyleForced,
      double uiFontScale,

      // --- Text box ---
      boolean textBoxAssetEnabled,
      @Nullable String textBoxImagePath,
      @Nullable String narrationTextBoxImagePath,
      double[] textBoxFillColor,
      double textBoxAssetOverlayOpacity,
      List<BoundsPointCodec.Point> textBoxBoundsPolygon,

      // --- Name box ---
      @Nullable String nameBoxImagePath,
      double[] nameBoxFillColor,
      double[] nameTextFillColor,
      double nameTextXAlign,
      double nameBoxRenderOpacity,
      List<BoundsPointCodec.Point> nameBoxBoundsPolygon,

      // --- Dialogue text ---
      double[] dialogueTextFillColor,
      double dialogueTextXAlign,
      List<BoundsPointCodec.Point> dialogueTextBoundsPolygon,

      // --- Continue indicator ---
      @Nullable String continueIndicatorImagePath,
      double continueIndicatorImageWidth,
      double continueIndicatorImageHeight,

      // --- NVL panel ---
      @Nullable String nvlPanelImagePath,
      double[] nvlPanelFillColor,
      double nvlPanelOpacity,
      double[] nvlSpeakerTextFillColor,
      double[] nvlTextFillColor,

      // --- Bubble dialogue ---
      @Nullable String bubbleImagePath,
      double[] bubbleFillColor,
      double bubbleOpacity,
      double[] bubbleBorderFillColor,
      double bubbleBorderWidth,
      double bubbleCornerRadius,
      double[] bubbleSpeakerTextFillColor,
      double[] bubbleTextFillColor,

      // --- Text box buttons ---
      boolean textBoxButtonsEnabled,
      List<VnUiActionButtonSpec> textBoxButtons
  ) {
    static RenderSettings defaults() {
      return new RenderSettings(
          new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_NAME_FONT_SIZE, true),
          new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_DIALOGUE_FONT_SIZE, false),
          new VnFontSpec(DEFAULT_FONT_FAMILY, 20, false),
          false,
          1.0,
          true, null, null, TEXTBOX_COLOR, 0.0, List.of(),
          null, NAME_BOX_COLOR, DEFAULT_NAME_TEXT_COLOR, 0.0, 1.0, List.of(),
          TEXT_COLOR, 0.0, List.of(),
          null, 0.0, 0.0,
          null, new double[] {20.0 / 255, 32.0 / 255, 56.0 / 255, 0.9}, 1.0,
          new double[] {0xA9 / 255.0, 0xBC / 255.0, 0xD9 / 255.0, 1.0}, TEXT_COLOR,
          null, new double[] {0x15 / 255.0, 0x22 / 255.0, 0x38 / 255.0, 0.96}, 0.96,
          new double[] {0xA9 / 255.0, 0xBC / 255.0, 0xD9 / 255.0, 1.0}, 2.0, 20.0,
          new double[] {1.0, 0xD7 / 255.0, 0x8A / 255.0, 1.0},
          new double[] {0xF1 / 255.0, 0xF5 / 255.0, 0xFF / 255.0, 1.0},
          true, List.of());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Test-seam field / setter (backs renderStandardDialogueBackdropOnly)
  // ─────────────────────────────────────────────────────────────────────────

  void setTextBoxAssetPath(String path) {
    this.textBoxAssetPath = path;
  }

  /**
   * Thin test seam exposing just the text-box backdrop drawing portion of
   * {@code renderStandardDialogue} (asset-or-fill background paint) without requiring a full
   * {@code DialogueLine}/{@code VnState}/{@code VnUiLayoutSpec} fixture.
   */
  void renderStandardDialogueBackdropOnly(double x, double y, double width, double height) {
    if (textBoxAssetPath != null && !textBoxAssetPath.isBlank()) {
      blitter.drawImage(textBoxAssetPath, x, y, width, height);
    } else {
      blitter.setFill(TEXTBOX_COLOR[0], TEXTBOX_COLOR[1], TEXTBOX_COLOR[2], TEXTBOX_COLOR[3]);
      blitter.fillRect(x, y, width, height);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Entry points
  // ─────────────────────────────────────────────────────────────────────────

  void renderDialogue(
      DialogueLine dialogue,
      VnState state,
      VnUiLayoutSpec uiLayout,
      double width,
      double height,
      int hoveredButtonIndex,
      RenderSettings settings) {
    if (dialogue == null) return;
    renderStandardDialogue(dialogue, state, uiLayout, width, height, hoveredButtonIndex, settings);
  }

  void renderNvlHistory(VnState state, VnUiLayoutSpec uiLayout, double width, double height, RenderSettings settings) {
    renderNvlEntries(collectNvlEntries(state, null, uiLayout), uiLayout, width, height, settings);
  }

  void renderNvlDialogue(DialogueLine dialogue, VnState state, VnUiLayoutSpec uiLayout, double width, double height, RenderSettings settings) {
    renderNvlEntries(collectNvlEntries(state, dialogue, uiLayout), uiLayout, width, height, settings);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Standard dialogue mode
  // ─────────────────────────────────────────────────────────────────────────

  void renderStandardDialogue(
      DialogueLine dialogue,
      VnState state,
      VnUiLayoutSpec uiLayout,
      double width,
      double height,
      int hoveredButtonIndex,
      RenderSettings settings) {
    if (dialogue == null) return;
    boolean defaultDialogueStyle = settings.defaultDialogueStyleForced();
    double[] activeTextBoxFillColor = defaultDialogueStyle ? TEXTBOX_COLOR : settings.textBoxFillColor();
    double[] activeNameBoxFillColor = defaultDialogueStyle ? NAME_BOX_COLOR : settings.nameBoxFillColor();
    double[] activeNameTextFillColor = resolveSpeakerColor(
        dialogue, defaultDialogueStyle ? DEFAULT_NAME_TEXT_COLOR : settings.nameTextFillColor());
    double[] activeDialogueTextFillColor = defaultDialogueStyle ? TEXT_COLOR : settings.dialogueTextFillColor();
    double fscale = settings.uiFontScale();
    VnFontSpec activeNameFont = defaultDialogueStyle
        ? new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_NAME_FONT_SIZE * fscale, true)
        : settings.nameFont();
    VnFontSpec activeDialogueFont = defaultDialogueStyle
        ? new VnFontSpec(DEFAULT_FONT_FAMILY, DEFAULT_DIALOGUE_FONT_SIZE * fscale, false)
        : settings.dialogueFont();
    double activeNameBoxOpacity = defaultDialogueStyle ? 1.0 : settings.nameBoxRenderOpacity();

    TextBoxGeometry textBox = computeTextBoxGeometry(uiLayout, width, height);
    double textBoxX = textBox.x();
    double textBoxY = textBox.y();
    double textBoxWidth = textBox.width();
    double textBoxHeight = textBox.height();

    // Draw text box background (asset if provided, otherwise default fill).
    String speakerName = dialogue.getSpeakerName() == null ? "" : dialogue.getSpeakerName();
    boolean hasSpeaker = !speakerName.isEmpty();
    boolean useTextBoxAsset = settings.textBoxAssetEnabled();
    String activeTextBoxImage = useTextBoxAsset
        ? (hasSpeaker || settings.narrationTextBoxImagePath() == null
            ? settings.textBoxImagePath()
            : settings.narrationTextBoxImagePath())
        : null;
    boolean clipTextBox = useTextBoxAsset && hasPolygon(settings.textBoxBoundsPolygon());
    if (clipTextBox) {
      // Known limitation: Blitter2D has no arbitrary-polygon clip primitive — draw unclipped.
      // See the class Javadoc "Known port limitations" note.
      blitter.push();
    }
    if (activeTextBoxImage != null) {
      blitter.drawImage(activeTextBoxImage, textBoxX, textBoxY, textBoxWidth, textBoxHeight);
      if (settings.textBoxAssetOverlayOpacity() > 0.001) {
        blitter.setFill(activeTextBoxFillColor[0], activeTextBoxFillColor[1], activeTextBoxFillColor[2],
            settings.textBoxAssetOverlayOpacity());
        blitter.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
      }
    } else {
      blitter.setFill(activeTextBoxFillColor[0], activeTextBoxFillColor[1], activeTextBoxFillColor[2], activeTextBoxFillColor[3]);
      blitter.fillRect(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
    }
    if (clipTextBox) blitter.pop();

    // Draw name box if speaker exists
    if (hasSpeaker) {
      double nameBoxX = textBoxX + uiLayout.nameBoxXOffset();
      double nameBoxY = textBoxY + uiLayout.nameBoxYOffset();
      double nameBoxW;
      if (uiLayout.nameBoxAutoWidth()) {
        double textW = computeTextWidth(speakerName, activeNameFont);
        nameBoxW = Math.max(textW + uiLayout.nameTextXOffset() * 2, uiLayout.nameBoxWidth());
      } else {
        nameBoxW = uiLayout.nameBoxWidth();
      }
      double nameBoxH = uiLayout.nameBoxHeight();
      String activeNameBoxImage = defaultDialogueStyle ? null : settings.nameBoxImagePath();
      boolean clipNameBox = !defaultDialogueStyle && hasPolygon(settings.nameBoxBoundsPolygon());
      if (clipNameBox) {
        // Known limitation: unclipped fallback — see class Javadoc.
        blitter.push();
      }
      if (activeNameBoxOpacity < 0.999) blitter.setGlobalAlpha(activeNameBoxOpacity);
      if (activeNameBoxImage != null) {
        blitter.drawImage(activeNameBoxImage, nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      } else {
        blitter.setFill(activeNameBoxFillColor[0], activeNameBoxFillColor[1], activeNameBoxFillColor[2], activeNameBoxFillColor[3]);
        blitter.fillRect(nameBoxX, nameBoxY, nameBoxW, nameBoxH);
      }
      if (activeNameBoxOpacity < 0.999) blitter.setGlobalAlpha(1.0);
      if (clipNameBox) blitter.pop();

      blitter.setFill(activeNameTextFillColor[0], activeNameTextFillColor[1], activeNameTextFillColor[2], activeNameTextFillColor[3]);
      blitter.setFont(activeNameFont.family(), activeNameFont.size(), activeNameFont.bold());
      double nameContentX = nameBoxX + uiLayout.nameTextXOffset();
      double nameContentW = Math.max(0, nameBoxW - uiLayout.nameTextXOffset() * 2);
      double nameTextW = computeTextWidth(speakerName, activeNameFont);
      double nameTextBaselineY = uiLayout.nameTextYAlign() >= 0.0
          ? resolvePaddedTextBaselineY(
              nameBoxY,
              nameBoxH,
              uiLayout.nameTextTopPadding(),
              uiLayout.nameTextBottomPadding(),
              activeNameFont,
              uiLayout.nameTextYAlign())
          : nameBoxY + uiLayout.nameTextBaselineOffset();
      blitter.drawText(
          speakerName,
          resolveAlignedTextX(nameContentX, nameContentW, nameTextW, settings.nameTextXAlign()),
          nameTextBaselineY,
          activeNameFont.size(),
          activeNameFont.bold());
    }

    // Parse and render dialogue text with effects
    String fullText = dialogue.getText() == null ? "" : dialogue.getText();
    List<TextSpan> spans = TextParser.parse(fullText);
    int plainLength = TextParser.plainLength(fullText);
    int revealedLength = state == null ? plainLength : Math.min(state.getTextRevealProgress(), plainLength);

    double textX = textBoxX + uiLayout.dialogueTextHorizontalPadding();
    double textTop = textBoxY + uiLayout.dialogueTextTopPadding();
    double textWidth = Math.max(
        60,
        textBoxWidth - uiLayout.dialogueTextHorizontalPadding() - uiLayout.dialogueTextRightPadding());
    double textHeight = Math.max(
        20,
        textBoxHeight - uiLayout.dialogueTextTopPadding() - uiLayout.dialogueTextBottomPadding());
    double textBaselineY = textTop + computeTextAscent(activeDialogueFont);
    blitter.push();
    if (!defaultDialogueStyle && hasPolygon(settings.dialogueTextBoundsPolygon())) {
      // Known limitation: unclipped fallback — see class Javadoc.
    } else {
      blitter.setClipRect(textX, textTop, textWidth, textHeight);
    }
    List<StyledLine> dialogueLines = layoutStyledLines(
        spans, revealedLength, textWidth, activeDialogueFont, activeDialogueTextFillColor);
    drawStyledLines(
        dialogueLines, textX, textBaselineY, textWidth, settings.dialogueTextXAlign(),
        activeDialogueFont, activeDialogueTextFillColor);
    blitter.pop();

    // Draw continue indicator if text is fully revealed
    if (state != null && revealedLength >= plainLength && state.isWaitingForInput()) {
      drawContinueIndicatorAfterText(
          dialogueLines, textX, textBaselineY, textWidth, settings.dialogueTextXAlign(), activeDialogueFont,
          textBoxX + textBoxWidth - 30, textBoxY + textBoxHeight - 20, settings);
    }

    renderTextBoxButtons(textBox, width, height, hoveredButtonIndex, state, settings);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  NVL mode
  // ─────────────────────────────────────────────────────────────────────────

  private void renderNvlEntries(List<DialogueRenderEntry> entries, VnUiLayoutSpec uiLayout, double width, double height, RenderSettings settings) {
    if (entries == null || entries.isEmpty()) return;
    double panelX = clamp(uiLayout.nvlX() * width, 0.0, width);
    double panelY = clamp(uiLayout.nvlY() * height, 0.0, height);
    double panelW = clamp(uiLayout.nvlWidth() * width, 120.0, width - panelX);
    double panelH = clamp(uiLayout.nvlHeight() * height, 80.0, height - panelY);
    double pad = uiLayout.nvlPadding();
    double speakerW = Math.max(40.0, uiLayout.nvlSpeakerWidth());
    double entryGap = Math.max(0.0, uiLayout.nvlEntryGap());
    double bodyGap = 16.0;

    drawPanel(settings.nvlPanelImagePath(), settings.nvlPanelFillColor(), settings.nvlPanelOpacity(),
        panelX, panelY, panelW, panelH, 18.0, null, 0.0);

    blitter.push();
    blitter.setClipRect(panelX, panelY, panelW, panelH);

    VnFontSpec nameFont = settings.nameFont();
    VnFontSpec dialogueFont = settings.dialogueFont();
    double y = panelY + pad + nameFont.size();
    double textX = panelX + pad + speakerW + bodyGap;
    double textWidth = Math.max(80.0, panelW - pad * 2 - speakerW - bodyGap);
    double speakerX = panelX + pad;

    for (DialogueRenderEntry entry : entries) {
      if (y > panelY + panelH) break;
      String speaker = entry.speaker() == null ? "" : entry.speaker();
      double[] speakerColor = parseColorOrDefault(entry.speakerColor(), settings.nvlSpeakerTextFillColor());
      blitter.setFill(speakerColor[0], speakerColor[1], speakerColor[2], speakerColor[3]);
      blitter.setFont(nameFont.family(), nameFont.size(), nameFont.bold());
      blitter.drawText(truncateText(speaker, Math.max(20.0, speakerW - 8.0), nameFont), speakerX, y,
          nameFont.size(), nameFont.bold());

      String text = entry.text() == null ? "" : entry.text();
      List<TextSpan> spans = TextParser.parse(text);
      int revealed = Math.min(entry.revealedChars(), TextParser.plainLength(text));
      double bodyTop = y;
      drawStyledText(spans, revealed, textX, bodyTop, textWidth, 0.0, dialogueFont, settings.nvlTextFillColor());
      double bodyHeight = measureStyledTextHeight(spans, revealed, textWidth, dialogueFont, settings.nvlTextFillColor());
      double entryHeight = Math.max(nameFont.size() * 1.2, bodyHeight);
      y += entryHeight + entryGap;
    }

    blitter.pop();
  }

  private List<DialogueRenderEntry> collectNvlEntries(VnState state, @Nullable DialogueLine currentDialogue, VnUiLayoutSpec uiLayout) {
    List<DialogueRenderEntry> entries = new ArrayList<>();
    if (state == null) return entries;
    List<com.jvn.core.vn.VnHistory.HistoryEntry> historyEntries = state.getHistory().getEntries();
    int maxEntries = Math.max(1, uiLayout.nvlMaxEntries());
    int start = Math.max(0, historyEntries.size() - maxEntries);
    for (int i = start; i < historyEntries.size(); i++) {
      var entry = historyEntries.get(i);
      String text = entry.getText() == null ? "" : entry.getText();
      entries.add(new DialogueRenderEntry(
          entry.getSpeaker(), entry.getSpeakerColor(), text, TextParser.plainLength(text)));
    }
    if (currentDialogue != null && !entries.isEmpty()) {
      String text = currentDialogue.getText() == null ? "" : currentDialogue.getText();
      entries.set(entries.size() - 1, new DialogueRenderEntry(
          currentDialogue.getSpeakerName(),
          currentDialogue.getSpeakerColor(),
          text,
          Math.min(state.getTextRevealProgress(), TextParser.plainLength(text))));
    }
    return entries;
  }

  private double[] resolveSpeakerColor(DialogueLine dialogue, double[] fallback) {
    if (dialogue == null) return fallback;
    return parseColorOrDefault(dialogue.getSpeakerColor(), fallback);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Bubble dialogue mode
  // ─────────────────────────────────────────────────────────────────────────

  void renderBubbleDialogue(
      DialogueLine dialogue,
      VnState state,
      VnCharacterScale characterScale,
      VnUiLayoutSpec uiLayout,
      double width,
      double height,
      RenderSettings settings) {
    if (dialogue == null) return;
    String speaker = dialogue.getSpeakerName();
    String fullText = dialogue.getText() == null ? "" : dialogue.getText();
    List<TextSpan> spans = TextParser.parse(fullText);
    int revealedChars = state == null
        ? TextParser.plainLength(fullText)
        : Math.min(state.getTextRevealProgress(), TextParser.plainLength(fullText));

    VnFontSpec nameFont = settings.nameFont();
    VnFontSpec dialogueFont = settings.dialogueFont();

    BubbleGeometry bubble = resolveBubbleGeometry(
        dialogue, state, characterScale, uiLayout, width, height, speaker, spans, revealedChars, settings);
    drawBubblePanel(bubble, settings);

    double pad = uiLayout.bubbleTextPadding();
    double textX = bubble.x() + pad;
    double contentWidth = Math.max(80.0, bubble.width() - pad * 2);
    double y = bubble.y() + pad + nameFont.size();
    if (speaker != null && !speaker.isBlank()) {
      double[] speakerColor = resolveSpeakerColor(dialogue, settings.bubbleSpeakerTextFillColor());
      blitter.setFill(speakerColor[0], speakerColor[1], speakerColor[2], speakerColor[3]);
      blitter.setFont(nameFont.family(), nameFont.size(), nameFont.bold());
      blitter.drawText(speaker, textX, y, nameFont.size(), nameFont.bold());
      y += nameFont.size() * 0.95;
    }
    drawStyledText(spans, revealedChars, textX, y, contentWidth, 0.0, dialogueFont, settings.bubbleTextFillColor());

    if (revealedChars >= TextParser.plainLength(fullText) && state != null && state.isWaitingForInput()) {
      double indicatorY = bubble.tailOnTop() ? bubble.y() + bubble.height() - 14.0 : bubble.y() + bubble.height() - 18.0;
      drawContinueIndicator(bubble.x() + bubble.width() - 24.0, indicatorY);
    }
  }

  /**
   * Bubble anchor placement. The original resolved this from live character-position/visual/
   * displacement state on {@code VnState} plus a {@code VnScenario} character lookup. That whole
   * resolution chain is out of scope for this dialogue-only collaborator's port (it belongs to a
   * character/positioning collaborator this plan splits out separately) — callers pre-resolve the
   * anchor point and scale via this small carrier instead.
   */
  record VnCharacterScale(double anchorXFraction, double anchorYFraction, double scale, boolean hasAnchor) {
    static VnCharacterScale none() {
      return new VnCharacterScale(0.5, 0.58, 1.0, false);
    }
  }

  private BubbleGeometry resolveBubbleGeometry(
      DialogueLine dialogue,
      VnState state,
      VnCharacterScale characterScale,
      VnUiLayoutSpec uiLayout,
      double width,
      double height,
      String speaker,
      List<TextSpan> spans,
      int revealedChars,
      RenderSettings settings) {
    double maxWidth = clamp(width * uiLayout.bubbleWidthFactor(), 180.0, Math.min(width - 32.0, 620.0));
    double pad = uiLayout.bubbleTextPadding();
    double contentWidth = Math.max(120.0, maxWidth - pad * 2);
    double textHeight = measureStyledTextHeight(spans, revealedChars, contentWidth, settings.dialogueFont(), settings.dialogueTextFillColor());
    double speakerHeight = (speaker == null || speaker.isBlank()) ? 0.0 : settings.nameFont().size() * 1.15;
    double bubbleH = Math.max(uiLayout.bubbleMinHeight(), pad * 2 + textHeight + speakerHeight);
    double tailSize = uiLayout.bubbleTailSize();

    VnCharacterScale scale = characterScale == null ? VnCharacterScale.none() : characterScale;
    double anchorX = width * scale.anchorXFraction();
    double anchorY = height * scale.anchorYFraction();

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

  private record DialogueRenderEntry(String speaker, String speakerColor, String text, int revealedChars) {}

  private record BubbleGeometry(
      double x, double y, double width, double height,
      double anchorX, double anchorY, double tailSize, boolean tailOnTop) {}

  private void drawPanel(
      @Nullable String assetPath, double[] fillColor, double opacity,
      double x, double y, double width, double height,
      double radius, @Nullable double[] borderColor, double borderWidth) {
    blitter.setGlobalAlpha(clamp(opacity, 0.0, 1.0));
    if (assetPath != null) {
      blitter.drawImage(assetPath, x, y, width, height);
    } else {
      blitter.setFill(fillColor[0], fillColor[1], fillColor[2], fillColor[3]);
      // Blitter2D has no rounded-rect primitive; square corners are an accepted simplification
      // (matching MenuBackgroundRenderer.fillRoundRect's own precedent for this same gap).
      blitter.fillRect(x, y, width, height);
    }
    blitter.setGlobalAlpha(1.0);
    if (borderColor != null && borderWidth > 0.0) {
      blitter.setStroke(borderColor[0], borderColor[1], borderColor[2], borderColor[3]);
      blitter.setStrokeWidth(borderWidth);
      blitter.strokeRect(x, y, width, height);
    }
  }

  private void drawBubblePanel(BubbleGeometry bubble, RenderSettings settings) {
    if (bubble == null) return;
    drawPanel(
        settings.bubbleImagePath(), settings.bubbleFillColor(), settings.bubbleOpacity(),
        bubble.x(), bubble.y(), bubble.width(), bubble.height(),
        settings.bubbleCornerRadius(), settings.bubbleBorderFillColor(), settings.bubbleBorderWidth());

    double tailHalf = bubble.tailSize() * 0.58;
    double tailCenter = clamp(
        bubble.anchorX(), bubble.x() + bubble.tailSize(), bubble.x() + bubble.width() - bubble.tailSize());
    double baseY = bubble.tailOnTop() ? bubble.y() : bubble.y() + bubble.height();
    double tipY = bubble.anchorY();
    double[] fillColor = settings.bubbleFillColor();
    blitter.setFill(fillColor[0], fillColor[1], fillColor[2], settings.bubbleOpacity());
    double[] tailXy = {tailCenter - tailHalf, baseY, tailCenter + tailHalf, baseY, bubble.anchorX(), tipY};
    blitter.fillPolygon(tailXy);
    double[] borderColor = settings.bubbleBorderFillColor();
    blitter.setStroke(borderColor[0], borderColor[1], borderColor[2], borderColor[3]);
    blitter.setStrokeWidth(settings.bubbleBorderWidth());
    blitter.strokePolygon(tailXy);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Text box buttons
  // ─────────────────────────────────────────────────────────────────────────

  private void renderTextBoxButtons(
      TextBoxGeometry textBox, double viewportWidth, double viewportHeight, int hoveredButtonIndex,
      VnState state, RenderSettings settings) {
    if (!settings.textBoxButtonsEnabled()) return;
    List<VnUiActionButtonSpec> textBoxButtons = settings.textBoxButtons();
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return;
    for (int i = 0; i < textBoxButtons.size(); i++) {
      VnUiActionButtonSpec button = textBoxButtons.get(i);
      if (button == null) continue;
      ButtonGeometry geometry = computeButtonGeometry(button, textBox, viewportWidth, viewportHeight);
      boolean hovered = i == hoveredButtonIndex;
      boolean enabled = button.enabled();

      @Nullable String asset = isBlank(button.assetPath()) ? null : button.assetPath();
      @Nullable String hoverAsset = isBlank(button.hoverAssetPath()) ? asset : button.hoverAssetPath();
      @Nullable String disabledAsset = isBlank(button.disabledAssetPath()) ? asset : button.disabledAssetPath();
      @Nullable String drawAsset = !enabled
          ? firstNonNull(disabledAsset, asset)
          : (hovered ? firstNonNull(hoverAsset, asset) : asset);
      boolean imageBacked = drawAsset != null;
      List<BoundsPointCodec.Point> buttonPolygon = parseBoundsPoints(button.boundsPoints());
      boolean clipButton = hasPolygon(buttonPolygon);
      if (imageBacked) {
        if (clipButton) {
          // Known limitation: unclipped fallback — see class Javadoc.
          blitter.push();
        }
        if (!enabled) blitter.setGlobalAlpha(0.55);
        blitter.drawImage(drawAsset, geometry.x(), geometry.y(), geometry.width(), geometry.height());
        blitter.setGlobalAlpha(1.0);
        if (clipButton) blitter.pop();
      } else {
        double[] fill = !enabled
            ? new double[] {38.0 / 255, 40.0 / 255, 48.0 / 255, 0.7}
            : (hovered ? new double[] {90.0 / 255, 120.0 / 255, 180.0 / 255, 0.8}
                       : new double[] {32.0 / 255, 36.0 / 255, 46.0 / 255, 0.78});
        blitter.setFill(fill[0], fill[1], fill[2], fill[3]);
        // Blitter2D has no rounded-rect primitive; square corners accepted (see drawPanel note).
        blitter.fillRect(geometry.x(), geometry.y(), geometry.width(), geometry.height());

        double[] stroke = !enabled
            ? new double[] {120.0 / 255, 125.0 / 255, 136.0 / 255, 0.75}
            : (hovered ? new double[] {170.0 / 255, 210.0 / 255, 255.0 / 255, 0.95}
                       : new double[] {120.0 / 255, 135.0 / 255, 170.0 / 255, 0.82});
        blitter.setStroke(stroke[0], stroke[1], stroke[2], stroke[3]);
        blitter.setStrokeWidth(hovered ? 2.0 : 1.2);
        if (clipButton) {
          double[] xy = flattenPolygon(buttonPolygon, geometry.x(), geometry.y(), geometry.width(), geometry.height());
          blitter.strokePolygon(xy);
        } else {
          blitter.strokeRect(geometry.x(), geometry.y(), geometry.width(), geometry.height());
        }

        double[] labelFill = !enabled
            ? new double[] {172.0 / 255, 176.0 / 255, 188.0 / 255, 0.75}
            : (hovered ? new double[] {245.0 / 255, 252.0 / 255, 255.0 / 255, 1.0}
                       : new double[] {225.0 / 255, 232.0 / 255, 246.0 / 255, 1.0});
        blitter.setFill(labelFill[0], labelFill[1], labelFill[2], labelFill[3]);
        VnFontSpec labelFont = new VnFontSpec(
            settings.choiceFont().family(), clamp(geometry.height() * 0.42, 10, 18), true);
        blitter.setFont(labelFont.family(), labelFont.size(), labelFont.bold());
        String label = button.label() == null || button.label().isBlank() ? button.id() : button.label();
        double textW = computeTextWidth(label, labelFont);
        double textX = geometry.x() + Math.max(8, (geometry.width() - textW) / 2.0);
        double textY = geometry.y() + geometry.height() * 0.64;
        blitter.drawText(label, textX, textY, labelFont.size(), labelFont.bold());
      }
    }
  }

  /**
   * Hit-tests the text-box buttons in top-most-first order, mirroring
   * {@link #renderTextBoxButtons}'s geometry/visibility rules exactly so hover state always
   * matches what was last drawn.
   */
  int getHoveredTextBoxButtonIndex(
      VnState state, VnUiLayoutSpec uiLayout, double width, double height, double mouseX, double mouseY,
      RenderSettings settings) {
    if (!settings.textBoxButtonsEnabled()) return -1;
    List<VnUiActionButtonSpec> textBoxButtons = settings.textBoxButtons();
    if (textBoxButtons == null || textBoxButtons.isEmpty()) return -1;
    TextBoxGeometry textBox = computeTextBoxGeometry(uiLayout, width, height);
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
            points, geometry.x(), geometry.y(), geometry.width(), geometry.height(), mouseX, mouseY);
      }
    }
    return mouseX >= geometry.x() && mouseX <= geometry.x() + geometry.width()
        && mouseY >= geometry.y() && mouseY <= geometry.y() + geometry.height();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Styled text layout / painting
  // ─────────────────────────────────────────────────────────────────────────

  private record StyledGlyph(
      char value, VnFontSpec font, double[] color, TextEffect effect, int glyphIndex, double width, boolean syntheticItalic) {}

  private record StyledLine(List<StyledGlyph> glyphs, double width) {}

  void drawStyledText(
      List<TextSpan> spans, int revealedChars, double startX, double startY, double maxWidth, double xAlign,
      VnFontSpec baseFont, double defaultR, double defaultG, double defaultB, double defaultA) {
    drawStyledText(spans, revealedChars, startX, startY, maxWidth, xAlign, baseFont,
        new double[] {defaultR, defaultG, defaultB, defaultA});
  }

  private void drawStyledText(
      List<TextSpan> spans, int revealedChars, double startX, double startY, double maxWidth, double xAlign,
      VnFontSpec baseFont, double[] defaultTextColor) {
    List<StyledLine> lines = layoutStyledLines(spans, revealedChars, maxWidth, baseFont, defaultTextColor);
    drawStyledLines(lines, startX, startY, maxWidth, xAlign, baseFont, defaultTextColor);
  }

  private List<StyledLine> layoutStyledLines(
      List<TextSpan> spans, int revealedChars, double maxWidth, VnFontSpec baseFont, double[] defaultTextColor) {
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
        double[] spanColor = span.hasColor() ? parseColorHex(span.getColorHex()) : defaultTextColor;
        VnFontSpec effectFont = baseFont;
        boolean syntheticItalic = false;
        if (span.getEffect() == TextEffect.BOLD) {
          effectFont = new VnFontSpec(baseFont.family(), baseFont.size(), true);
        } else if (span.getEffect() == TextEffect.ITALIC) {
          // Always synthetic — see class Javadoc "Known port limitations".
          effectFont = baseFont;
          syntheticItalic = true;
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

          currentLine.add(new StyledGlyph(c, effectFont, spanColor, span.getEffect(), glyphIndex, charWidth, syntheticItalic));
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
      List<StyledLine> lines, double startX, double startY, double maxWidth, double xAlign,
      VnFontSpec baseFont, double[] defaultTextColor) {
    double lineHeight = Math.max(22.0, baseFont.size() * 1.15);
    long animationTimeMs = System.nanoTime() / 1_000_000L;
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      StyledLine line = lines.get(lineIndex);
      double x = resolveAlignedTextX(startX, maxWidth, line.width(), xAlign);
      double y = startY + lineIndex * lineHeight;

      for (StyledGlyph glyph : line.glyphs()) {
        double[] color = glyph.color();
        blitter.setFill(color[0], color[1], color[2], color[3]);
        blitter.setFont(glyph.font().family(), glyph.font().size(), glyph.font().bold());

        double offsetX = 0.0;
        double offsetY = 0.0;
        double effectPhase = (animationTimeMs * 0.01) + glyph.glyphIndex() * 0.3;
        switch (glyph.effect()) {
          case SHAKE -> {
            offsetX = (Math.random() - 0.5) * 3;
            offsetY = (Math.random() - 0.5) * 3;
          }
          case WAVE -> offsetY = Math.sin(effectPhase) * 3;
          case BOUNCE -> offsetY = Math.abs(Math.sin(effectPhase * 2)) * -4;
          case RAINBOW -> {
            double[] rainbow = hsbToRgb((effectPhase * 50) % 360, 0.8, 1.0);
            blitter.setFill(rainbow[0], rainbow[1], rainbow[2], 1.0);
          }
          default -> {}
        }

        double drawX = x + offsetX;
        double drawY = y + offsetY;
        if (glyph.syntheticItalic()) {
          blitter.push();
          blitter.transform(1.0, 0.0, SYNTHETIC_ITALIC_SHEAR, 1.0, -SYNTHETIC_ITALIC_SHEAR * drawY, 0.0);
          blitter.drawText(String.valueOf(glyph.value()), drawX, drawY, glyph.font().size(), glyph.font().bold());
          blitter.pop();
        } else {
          blitter.drawText(String.valueOf(glyph.value()), drawX, drawY, glyph.font().size(), glyph.font().bold());
        }
        x += glyph.width();
      }
    }

    blitter.setFont(baseFont.family(), baseFont.size(), baseFont.bold());
    blitter.setFill(defaultTextColor[0], defaultTextColor[1], defaultTextColor[2], defaultTextColor[3]);
  }

  private void drawContinueIndicatorAfterText(
      List<StyledLine> lines, double startX, double startY, double maxWidth, double xAlign, VnFontSpec baseFont,
      double fallbackX, double fallbackY, RenderSettings settings) {
    String continueIndicatorImage = settings.continueIndicatorImagePath();
    if (continueIndicatorImage == null || lines == null || lines.isEmpty()) {
      drawContinueIndicator(fallbackX, fallbackY);
      return;
    }

    StyledLine lastLine = lines.get(lines.size() - 1);
    double lineHeight = Math.max(22.0, baseFont.size() * 1.15);
    double lineX = resolveAlignedTextX(startX, maxWidth, lastLine.width(), xAlign);
    double imageWidth = settings.continueIndicatorImageWidth();
    double imageHeight = settings.continueIndicatorImageHeight();
    double x = lineX + lastLine.width() + 4.0;
    double y = startY + (lines.size() - 1) * lineHeight - imageHeight + baseFont.size() * 0.42;
    if (x + imageWidth > startX + maxWidth) {
      x = startX + maxWidth - imageWidth;
      y += lineHeight;
    }
    blitter.drawImage(continueIndicatorImage, x, y, imageWidth, imageHeight);
  }

  private double measureStyledTextHeight(List<TextSpan> spans, int revealedChars, double maxWidth, VnFontSpec baseFont, double[] defaultTextColor) {
    List<StyledLine> lines = layoutStyledLines(spans, revealedChars, maxWidth, baseFont, defaultTextColor);
    double lineHeight = Math.max(22.0, baseFont.size() * 1.15);
    return Math.max(lineHeight, lines.size() * lineHeight);
  }

  private void drawWrappedText(String text, double x, double y, double maxWidth, VnFontSpec font) {
    blitter.setFont(font.family(), font.size(), font.bold());
    String[] words = text.split(" ");
    StringBuilder line = new StringBuilder();
    double currentY = y;
    double lineHeight = 22;

    for (String word : words) {
      int originalLength = line.length();
      if (originalLength > 0) {
        line.append(' ');
      }
      line.append(word);
      double testWidth = computeTextWidth(line.toString(), font);

      if (testWidth > maxWidth && originalLength > 0) {
        line.setLength(originalLength);
        blitter.drawText(line.toString(), x, currentY, font.size(), font.bold());
        line.setLength(0);
        line.append(word);
        currentY += lineHeight;
      }
    }

    if (line.length() > 0) {
      blitter.drawText(line.toString(), x, currentY, font.size(), font.bold());
    }
  }

  private void drawContinueIndicator(double x, double y) {
    long animationTimeMs = System.nanoTime() / 1_000_000L;
    double bounce = Math.sin(animationTimeMs * 0.005) * 4;
    double animY = y + bounce;

    blitter.setFill(TEXT_COLOR[0], TEXT_COLOR[1], TEXT_COLOR[2], TEXT_COLOR[3]);
    double[] xy = {x, animY, x + 10, animY, x + 5, animY + 10};
    blitter.fillPolygon(xy);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Text measurement / geometry helpers
  // ─────────────────────────────────────────────────────────────────────────

  double computeTextWidth(String text, VnFontSpec font) {
    // Routed through measureTextMetrics (not the bare measureTextWidth(text, size, bold)) because
    // FxBlitter2D.measureTextWidth resolves the font FAMILY from persistent canvas state (the last
    // setFont call), not from any argument here — only measureTextMetrics takes family explicitly.
    // Without this, a theme's non-default font family measures against whatever family a sibling
    // collaborator (or an earlier frame) last left on the canvas, while drawStyledLines paints in
    // the correct family — causing visible glyph/advance-width mismatches (overlap or gaps) and
    // wrap points computed against the wrong width.
    return blitter.measureTextMetrics(text, font.family(), font.size(), font.bold()).width();
  }

  double computeTextAscent(VnFontSpec font) {
    return blitter.measureTextMetrics(text(font), font.family(), font.size(), font.bold()).ascent();
  }

  double computeTextHeight(VnFontSpec font) {
    return blitter.measureTextMetrics(text(font), font.family(), font.size(), font.bold()).lineHeight();
  }

  /** Placeholder sample text used only to obtain font metrics independent of any drawn string. */
  private static String text(VnFontSpec font) {
    return "Mg";
  }

  private double resolvePaddedTextBaselineY(
      double boxY, double boxHeight, double topPadding, double bottomPadding, VnFontSpec font, double yAlign) {
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

  private String truncateText(String text, double maxWidth, VnFontSpec font) {
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

  private TextBoxGeometry computeTextBoxGeometry(VnUiLayoutSpec uiLayout, double width, double height) {
    double textBoxX = clamp(width * uiLayout.textBoxX(), 0, width);
    double textBoxY = clamp(height * uiLayout.textBoxY(), 0, height);
    double maxBoxWidth = Math.max(1, width - textBoxX);
    double maxBoxHeight = Math.max(1, height - textBoxY);
    double textBoxWidth = clamp(width * uiLayout.textBoxWidth(), 1, maxBoxWidth);
    double textBoxHeight = clamp(height * uiLayout.textBoxHeight(), 1, maxBoxHeight);
    return new TextBoxGeometry(textBoxX, textBoxY, textBoxWidth, textBoxHeight);
  }

  private ButtonGeometry computeButtonGeometry(
      VnUiActionButtonSpec button, TextBoxGeometry textBox, double viewportWidth, double viewportHeight) {
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

  private record TextBoxGeometry(double x, double y, double width, double height) {}

  private record ButtonGeometry(double x, double y, double width, double height) {}

  // ─────────────────────────────────────────────────────────────────────────
  //  Color / polygon parsing helpers
  // ─────────────────────────────────────────────────────────────────────────

  private double[] parseColorHex(String hex) {
    if (hex == null || hex.isEmpty()) return TEXT_COLOR;
    try {
      String h = hex.startsWith("#") ? hex.substring(1) : hex;
      if (h.length() == 6) {
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return new double[] {r / 255.0, g / 255.0, b / 255.0, 1.0};
      }
    } catch (Exception ignored) {
      // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return TEXT_COLOR;
  }

  /** Parses a {@code #RRGGBB}/{@code #RRGGBBAA} hex color string, or returns {@code fallback}. */
  private double[] parseColorOrDefault(String raw, double[] fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      String h = raw.trim();
      if (h.startsWith("#")) h = h.substring(1);
      if (h.length() == 6) {
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return new double[] {r / 255.0, g / 255.0, b / 255.0, 1.0};
      }
      if (h.length() == 8) {
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        int a = Integer.parseInt(h.substring(6, 8), 16);
        return new double[] {r / 255.0, g / 255.0, b / 255.0, a / 255.0};
      }
    } catch (Exception ignored) {
      // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return fallback;
  }

  private List<BoundsPointCodec.Point> parseBoundsPoints(String raw) {
    List<BoundsPointCodec.Point> parsed = BoundsPointCodec.parse(raw);
    return parsed.size() >= 3 ? parsed : List.of();
  }

  private boolean hasPolygon(List<BoundsPointCodec.Point> points) {
    return points != null && points.size() >= 3;
  }

  private double[] flattenPolygon(List<BoundsPointCodec.Point> localPoints, double rectX, double rectY, double rectW, double rectH) {
    double[] xy = new double[localPoints.size() * 2];
    for (int i = 0; i < localPoints.size(); i++) {
      BoundsPointCodec.Point point = localPoints.get(i);
      xy[i * 2] = rectX + rectW * point.x();
      xy[i * 2 + 1] = rectY + rectH * point.y();
    }
    return xy;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static @Nullable String firstNonNull(@Nullable String primary, @Nullable String fallback) {
    return primary != null ? primary : fallback;
  }

  /** HSB to RGB, replacing {@code javafx.scene.paint.Color.hsb} for the RAINBOW text effect. */
  private static double[] hsbToRgb(double hue, double saturation, double brightness) {
    double h = ((hue % 360) + 360) % 360;
    double c = brightness * saturation;
    double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
    double m = brightness - c;
    double r, g, b;
    if (h < 60) { r = c; g = x; b = 0; }
    else if (h < 120) { r = x; g = c; b = 0; }
    else if (h < 180) { r = 0; g = c; b = x; }
    else if (h < 240) { r = 0; g = x; b = c; }
    else if (h < 300) { r = x; g = 0; b = c; }
    else { r = c; g = 0; b = x; }
    return new double[] {r + m, g + m, b + m};
  }
}
