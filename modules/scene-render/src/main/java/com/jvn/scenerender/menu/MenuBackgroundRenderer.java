package com.jvn.scenerender.menu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.BoundedImageCache;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.scenerender.assets.AssetDimensionProbe;

/**
 * Screen-background painting (solid/frosted/gameplay-backdrop/logo/header/hints) plus the shared
 * geometry, font, colour, and asset-dimension helpers that every other {@code menu} package
 * collaborator (list, save/load slots, settings sliders, gallery/music-room) reuses. Holds the
 * live {@link MenuTheme} and UI font scale on behalf of {@link MenuRenderer}'s facade so sibling
 * collaborators always see the current values without each needing their own copy.
 */
final class MenuBackgroundRenderer {
  private static final double SUBMENU_BACKGROUND_BLUR_RADIUS = 14.0;
  // SUBMENU_FROST_TINT = Color.rgb(224, 236, 255, 0.28) expressed as normalised Blitter2D RGBA.
  private static final double SUBMENU_FROST_TINT_R = 224.0 / 255.0;
  private static final double SUBMENU_FROST_TINT_G = 236.0 / 255.0;
  private static final double SUBMENU_FROST_TINT_B = 255.0 / 255.0;
  private static final double SUBMENU_FROST_TINT_A = 0.28;

  private final Blitter2D blitter;
  private final AssetCatalog assetCatalog;
  private final BoundedImageCache<Object> imageCache = new BoundedImageCache<>(128);

  /**
   * The active project's root directory, if configured. Consulted by {@link #imageDimensions}
   * as the last of {@link AssetDimensionProbe}'s fallback tiers (parity with
   * {@code FxBlitter2D.resolveMediaUrl}'s own {@code projectRoot}-relative filesystem fallback),
   * so a themed project's image assets resolve for layout math the same way they resolve when
   * actually drawn.
   *
   * <p><b>Not</b> consulted for font resolution: {@link Blitter2D#setFont(String, double, boolean)}
   * takes only a bare family name, with no way to load a custom {@code .ttf}/{@code .otf} file
   * from a project directory in a JavaFX-free module. A theme's configured custom project font
   * falls back to whatever the underlying backend resolves system-wide for that family name.
   * Per-backend font registration (loading a project's font files into that backend's own font
   * system) would be the way to restore true custom-font support.
   */
  private File projectRoot;

  MenuTheme theme;
  double activeUiFontScale = 1.0;

  MenuBackgroundRenderer(Blitter2D blitter, AssetCatalog assetCatalog) {
    this.blitter = blitter;
    this.assetCatalog = assetCatalog;
    this.theme = MenuTheme.defaults();
  }

  void setProjectRoot(File root) {
    if (!java.util.Objects.equals(projectRoot, root)) imageCache.clear();
    this.projectRoot = root;
  }

  void clearImageCache() {
    imageCache.clear();
  }

  // --- Backdrop / background painting ---

  void clear(double w, double h) {
    // Note: w/h are unused now that Blitter2D.clear() clears the whole canvas directly,
    // rather than filling an explicit rect as the JavaFX gc-based version did. Kept as
    // parameters since every call site passes the current canvas size.
    MenuTheme.ColorSpec bg = theme.getBackgroundColor();
    blitter.clear(bg.r(), bg.g(), bg.b(), bg.a());
  }

  void drawGameplayMenuBackdrop(double w, double h) {
    MenuTheme.ColorSpec dim = theme.getGameplayDimColor();
    if (dim == null) dim = new MenuTheme.ColorSpec(0.0, 0.0, 0.0, 0.50);
    blitter.setFill(dim.r(), dim.g(), dim.b(), dim.a());
    blitter.fillRect(0, 0, w, h);

    String panelPath = theme.getGameplayPanelImagePath();
    double[] panelDims = imageDimensions(panelPath).orElse(null);
    double panelX = w / 2.0;
    double panelY = h / 2.0;
    double panelScale = theme.getGameplayPanelScale();
    boolean hasPanel = panelDims != null;
    if (hasPanel) {
      double panelW = panelDims[0] * panelScale;
      double panelH = panelDims[1] * panelScale;
      panelX = (w - panelW) / 2.0;
      panelY = (h - panelH) / 2.0;
      blitter.drawImage(panelPath, panelX, panelY, panelW, panelH);
      panelX += panelW / 2.0;
    }

    String logoPath = theme.getGameplayLogoImagePath();
    double[] logoDims = imageDimensions(logoPath).orElse(null);
    if (logoDims != null) {
      double logoScale = theme.getGameplayLogoScale();
      double logoW = logoDims[0] * logoScale;
      double logoH = logoDims[1] * logoScale;
      double logoX = panelX - logoW / 2.0;
      double logoY = !hasPanel ? h * 0.08 : panelY + 18.0;
      blitter.drawImage(logoPath, logoX, logoY, logoW, logoH);
    }
  }

  void drawScreenBackground(double w, double h, MenuStyleSpec style, boolean allowThemeImageFallback) {
    drawScreenBackground(w, h, style, allowThemeImageFallback, null);
  }

  void drawScreenBackground(double w, double h, MenuStyleSpec style, boolean allowThemeImageFallback, String screenBgAsset) {
    if (screenBgAsset != null && !screenBgAsset.isBlank()) {
      if (imageDimensions(screenBgAsset).isPresent()) {
        // Always paint a base layer first so transparent menu background PNGs
        // do not leak pixels from the previously rendered scene.
        MenuTheme.ColorSpec base = parseColorRgba(style != null ? style.backgroundColor() : null, theme.getBackgroundColor());
        if (base == null) base = new MenuTheme.ColorSpec(0.0, 0.0, 0.0, 1.0);
        double baseOpacity = style != null && style.backgroundOpacity() != null
            ? clamp01(style.backgroundOpacity())
            : 1.0;
        blitter.setFill(base.r(), base.g(), base.b(), baseOpacity);
        blitter.fillRect(0, 0, w, h);
        blitter.drawImage(screenBgAsset, 0, 0, w, h);
        return;
      }
    }
    String styleAsset = style != null ? style.backgroundAssetPath() : null;
    if (styleAsset != null && !styleAsset.isBlank()) {
      if (imageDimensions(styleAsset).isPresent()) {
        double alpha = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : 1.0;
        if (isFrostedOverlayStyle(style)) {
          drawBlurredSubmenuBackground(styleAsset, w, h, alpha);
          return;
        }
        blitter.push();
        blitter.setGlobalAlpha(alpha);
        blitter.drawImage(styleAsset, 0, 0, w, h);
        blitter.pop();
        return;
      }
    }

    // Backward-compatibility: older submenu/slot styles may not inherit a background asset.
    // In that case, use the themed main-menu background as the frosted source.
    if (isFrostedOverlayStyle(style) && theme.getBackgroundImagePath() != null) {
      String fallbackFrostSource = theme.getBackgroundImagePath();
      if (imageDimensions(fallbackFrostSource).isPresent()) {
        double alpha = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : 1.0;
        drawBlurredSubmenuBackground(fallbackFrostSource, w, h, alpha);
        return;
      }
    }

    // Submenus should visually stay tied to the main menu background.
    // If no image source exists, use themed background color plus frosted tint
    // instead of falling back to slot/submenu hardcoded colors.
    if (isFrostedOverlayStyle(style)) {
      MenuTheme.ColorSpec base = theme.getBackgroundColor() == null
          ? new MenuTheme.ColorSpec(0.0, 0.0, 0.0, 1.0)
          : theme.getBackgroundColor();
      double opacity = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : base.a();
      blitter.setFill(base.r(), base.g(), base.b(), opacity);
      blitter.fillRect(0, 0, w, h);
      blitter.setFill(SUBMENU_FROST_TINT_R, SUBMENU_FROST_TINT_G, SUBMENU_FROST_TINT_B, SUBMENU_FROST_TINT_A);
      blitter.fillRect(0, 0, w, h);
      return;
    }

    String styleColorRaw = style != null ? style.backgroundColor() : null;
    if (styleColorRaw != null && !styleColorRaw.isBlank()) {
      MenuTheme.ColorSpec color = parseColorRgba(styleColorRaw, theme.getBackgroundColor());
      double opacity = style != null && style.backgroundOpacity() != null ? clamp01(style.backgroundOpacity()) : color.a();
      blitter.setFill(color.r(), color.g(), color.b(), opacity);
      blitter.fillRect(0, 0, w, h);
      return;
    }

    if (allowThemeImageFallback && theme.getBackgroundImagePath() != null) {
      drawBackgroundImage(theme.getBackgroundImagePath(), w, h);
      return;
    }
    clear(w, h);
  }

  private boolean isFrostedOverlayStyle(MenuStyleSpec style) {
    if (style == null || style.id() == null) return false;
    String id = style.id().trim().toLowerCase();
    return "submenu".equals(id) || "slot".equals(id) || "settings".equals(id);
  }

  private void drawBlurredSubmenuBackground(String imagePath, double w, double h, double alpha) {
    blitter.push();
    blitter.setGlobalAlpha(alpha);
    blitter.setBlurRadius(SUBMENU_BACKGROUND_BLUR_RADIUS);
    blitter.drawImage(imagePath, 0, 0, w, h);
    blitter.setBlurRadius(0);
    blitter.pop();

    // Frosted overlay to create the "iced glass" submenu surface.
    blitter.setFill(SUBMENU_FROST_TINT_R, SUBMENU_FROST_TINT_G, SUBMENU_FROST_TINT_B, SUBMENU_FROST_TINT_A);
    blitter.fillRect(0, 0, w, h);
  }

  private void drawBackgroundImage(String path, double w, double h) {
    if (imageDimensions(path).isPresent()) {
      // Draw scaled to fill
      blitter.drawImage(path, 0, 0, w, h);
    } else {
      clear(w, h);
    }
  }

  void drawLogo(String path, double w, double h) {
    double[] dims = imageDimensions(path).orElse(null);
    if (dims == null) return;

    double scale = theme.getLogoScale();
    double logoW = dims[0] * scale;
    double logoH = dims[1] * scale;

    // Calculate position (logoX/logoY are fractions)
    double x = w * theme.getLogoX() - logoW / 2;
    double y = h * theme.getLogoY();

    // Draw shadow if enabled
    if (theme.isLogoShadow()) {
      blitter.setGlobalAlpha(0.4);
      blitter.drawImage(path, x + 4, y + 4, logoW, logoH);
      blitter.setGlobalAlpha(1.0);
    }

    blitter.drawImage(path, x, y, logoW, logoH);
  }

  /**
   * Resolves the pixel dimensions of an image asset for layout math, trying the given path and
   * then the same filename-fallback candidates the legacy JavaFX {@code loadImage} used. Unlike
   * that method, this does not decode/retain pixel data — {@link Blitter2D#drawImage} is handed
   * the classpath string directly and each backend resolves/caches the pixels itself.
   */
  java.util.Optional<double[]> imageDimensions(String path) {
    if (path == null || path.isBlank()) return java.util.Optional.empty();
    Object cachedMarker = imageCache.get(path);
    if (cachedMarker instanceof double[] cachedDims) return java.util.Optional.of(cachedDims);

    List<String> candidates = new ArrayList<>();
    candidates.add(path);
    candidates.addAll(buildFallbackImageCandidates(path));

    for (String candidate : candidates) {
      java.util.Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(assetCatalog, candidate, projectRoot);
      if (dims.isPresent()) {
        imageCache.put(path, dims.get());
        imageCache.put(candidate, dims.get());
        return dims;
      }
    }
    return java.util.Optional.empty();
  }

  private List<String> buildFallbackImageCandidates(String originalPath) {
    List<String> out = new ArrayList<>();
    String lower = originalPath.toLowerCase();
    int dot = originalPath.lastIndexOf('.');
    String base = dot > 0 ? originalPath.substring(0, dot) : originalPath;
    if (dot > 0) {
      out.add(base + ".png");
      out.add(base + ".jpg");
      out.add(base + ".jpeg");
      out.add(base + ".webp");
      out.add(base + ".bmp");
      out.add(base + ".gif");
    }

    // Generic sibling fallbacks for old projects that may not have the same filename set.
    int slash = originalPath.lastIndexOf('/');
    if (slash > 0) {
      String parent = originalPath.substring(0, slash + 1);
      out.add(parent + "field.jpg");
      out.add(parent + "field.png");
    }
    return out;
  }

  private void drawTitle(String text, double w, double y) {
    drawTitle(text, w, y, null, null);
  }

  private void drawTitle(String text, double w, double y, MenuStyleSpec style) {
    drawTitle(text, w, y, style, null);
  }

  private void drawTitle(String text, double w, double y, MenuStyleSpec style, MenuLayoutSpec layout) {
    if (text == null || text.isBlank()) text = "JVN";
    MenuTheme.ColorSpec titleColor = parseColorRgba(style != null ? style.titleColor() : null, theme.getTitleColor());
    MenuTheme.FontSpec titleFont = resolveTitleFontSpec(style);
    blitter.setFont(titleFont.family(), titleFont.size(), titleFont.bold());
    double tx = resolveTitleAlignedX(text, titleFont, w, layout);

    // Title shadow
    String shadowRaw = style != null ? style.titleShadowColor() : null;
    if (shadowRaw != null && !shadowRaw.isBlank()) {
      MenuTheme.ColorSpec shadow = parseColorRgba(shadowRaw, null);
      if (shadow != null) {
        blitter.setFill(shadow.r(), shadow.g(), shadow.b(), shadow.a());
        blitter.drawText(text, tx + 2, y + 2, titleFont.size(), titleFont.bold());
      }
    }

    blitter.setFill(titleColor.r(), titleColor.g(), titleColor.b(), titleColor.a());
    blitter.drawText(text, tx, y, titleFont.size(), titleFont.bold());
  }

  void drawHeader(String title, String subtitle, double w, double titleY, MenuStyleSpec style, MenuLayoutSpec layout) {
    drawTitle(title, w, titleY, style, layout);
    if (subtitle == null || subtitle.isBlank()) return;
    MenuTheme.FontSpec titleFont = resolveTitleFontSpec(style);
    MenuTheme.FontSpec subtitleFont = resolveSubtitleFontSpec(style);
    double subtitleY = titleY + Math.max(titleFont.size() * 0.82, subtitleFont.size()) + (layout != null ? layout.subtitleGap() : 12.0);
    drawSubtitle(subtitle, w, subtitleY, style, layout, subtitleFont);
  }

  private void drawSubtitle(String text, double w, double y, MenuStyleSpec style, MenuLayoutSpec layout, MenuTheme.FontSpec subtitleFont) {
    if (text == null || text.isBlank()) return;
    MenuTheme.ColorSpec subtitleColor = parseColorRgba(style != null ? style.hintsColor() : null,
        parseColorRgba(style != null ? style.titleColor() : null, theme.getHintColor()));
    blitter.setFont(subtitleFont.family(), subtitleFont.size(), subtitleFont.bold());
    double tx = resolveTitleAlignedX(text, subtitleFont, w, layout);
    blitter.setFill(subtitleColor.r(), subtitleColor.g(), subtitleColor.b(), subtitleColor.a());
    blitter.drawText(text, tx, y, subtitleFont.size(), subtitleFont.bold());
  }

  double resolveTitleAlignedX(String text, MenuTheme.FontSpec font, double w, MenuLayoutSpec layout) {
    double textW = blitter.measureTextWidth(text, font.size(), font.bold());
    Double txOverride = layout != null ? layout.titleX() : null;
    String align = layout != null ? layout.titleAlign() : "center";
    double tx = txOverride != null
        ? w * txOverride - textW / 2.0
        : switch (align == null ? "center" : align.toLowerCase()) {
          case "left" -> 24.0;
          case "right" -> w - textW - 24.0;
          default -> (w - textW) / 2.0;
        };
    return clamp(tx, 0, Math.max(0, w - textW));
  }

  /**
   * Font resolution for drawHeader/drawHints. Unlike JavaFX's font loading, this does not
   * delegate to a project-directory font resolver for custom TTF/OTF loading — Blitter2D.setFont
   * takes a plain family name and each backend is responsible for its own font resolution.
   */
  MenuTheme.FontSpec resolveTitleFontSpec(MenuStyleSpec style) {
    MenuTheme.FontSpec themeFont = theme.getTitleFontSpec();
    if (style == null) return scaledFontSpec(themeFont);
    String family = firstNonBlank(style.titleFontFamily(), themeFont.family());
    double size = (style.titleFontSize() != null ? style.titleFontSize() : themeFont.size()) * activeUiFontScale;
    boolean bold = parseBoldWeight(style.titleFontWeight(), true);
    return new MenuTheme.FontSpec(family, size, bold);
  }

  MenuTheme.FontSpec resolveSubtitleFontSpec(MenuStyleSpec style) {
    MenuTheme.FontSpec titleFont = resolveTitleFontSpec(style);
    MenuTheme.FontSpec hintFont = resolveHintFontSpec(style);
    String family = style != null ? firstNonBlank(style.titleFontFamily(), hintFont.family(), titleFont.family()) : hintFont.family();
    double size = Math.max(14.0, Math.min(titleFont.size() * 0.55, hintFont.size() * 1.35));
    boolean bold = style != null
        && parseBoldWeight(firstNonBlank(style.titleFontWeight(), style.hintsFontWeight()), false);
    return new MenuTheme.FontSpec(family, size, bold);
  }

  MenuTheme.FontSpec resolveHintFontSpec(MenuStyleSpec style) {
    MenuTheme.FontSpec themeFont = theme.getHintFontSpec();
    if (style == null) return scaledFontSpec(themeFont);
    String family = firstNonBlank(style.hintsFontFamily(), themeFont.family());
    double size = (style.hintsFontSize() != null ? style.hintsFontSize() : themeFont.size()) * activeUiFontScale;
    boolean bold = parseBoldWeight(style.hintsFontWeight(), false);
    return new MenuTheme.FontSpec(family, size, bold);
  }

  MenuTheme.FontSpec scaledFontSpec(MenuTheme.FontSpec font) {
    if (font == null || Math.abs(activeUiFontScale - 1.0) < 0.0001) return font;
    return new MenuTheme.FontSpec(font.family(), font.size() * activeUiFontScale, font.bold());
  }

  /** Reduces a JavaFX-style font-weight name to the bold/non-bold distinction Blitter2D exposes. */
  boolean parseBoldWeight(String raw, boolean def) {
    if (raw == null || raw.isBlank()) return def;
    String t = raw.trim().toUpperCase();
    if ("NORMAL".equals(t) || "THIN".equals(t) || "LIGHT".equals(t) || "MEDIUM".equals(t)) return false;
    if (t.contains("BOLD") || "BLACK".equals(t) || "HEAVY".equals(t)) return true;
    return def;
  }

  /** Colour parsing for drawScreenBackground/drawHeader/drawHints; returns Blitter2D-ready RGBA. */
  MenuTheme.ColorSpec parseColorRgba(String raw, MenuTheme.ColorSpec def) {
    if (raw == null || raw.isBlank()) return def;
    String t = raw.trim();
    try {
      if (t.startsWith("#")) {
        String hex = t.substring(1);
        if (hex.length() == 6) {
          int r = Integer.parseInt(hex.substring(0, 2), 16);
          int g = Integer.parseInt(hex.substring(2, 4), 16);
          int b = Integer.parseInt(hex.substring(4, 6), 16);
          return MenuTheme.ColorSpec.rgb255(r, g, b);
        }
        if (hex.length() == 8) {
          int a = Integer.parseInt(hex.substring(0, 2), 16);
          int r = Integer.parseInt(hex.substring(2, 4), 16);
          int g = Integer.parseInt(hex.substring(4, 6), 16);
          int b = Integer.parseInt(hex.substring(6, 8), 16);
          return MenuTheme.ColorSpec.rgb255(r, g, b, a / 255.0);
        }
      } else if (t.toLowerCase().startsWith("rgb")) {
        int lp = t.indexOf('(');
        int rp = t.indexOf(')');
        if (lp >= 0 && rp > lp) {
          String[] parts = t.substring(lp + 1, rp).split(",");
          double r = Double.parseDouble(parts[0].trim());
          double g = Double.parseDouble(parts[1].trim());
          double b = Double.parseDouble(parts[2].trim());
          double a = parts.length >= 4 ? Double.parseDouble(parts[3].trim()) : 1.0;
          if (r > 1 || g > 1 || b > 1 || a > 1) {
            return MenuTheme.ColorSpec.rgb255((int) r, (int) g, (int) b, a > 1 ? (a / 255.0) : a);
          }
          return new MenuTheme.ColorSpec(r, g, b, a);
        }
      }
    } catch (Exception ignored) {
      // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return def;
  }

  /**
   * Approximates {@code GraphicsContext.fillRoundRect} as a plain {@link Blitter2D#fillRect}.
   * Blitter2D has no path-integrated rounded-rect primitive: on the Swing backend, fillArc is a
   * standalone Arc2D.PIE fill disconnected from the Path2D built by beginPath/lineTo, and the
   * production FX backend (FxBlitter2D) does not override fillArc/strokeArc at all, falling
   * through to Blitter2D's default RenderDiagnostics.unsupported(...) stub. Square corners are a
   * deliberate, accepted visual simplification. The arcW/arcH parameters are kept (unused) so
   * call sites don't need to change if a rounded-rect primitive is added later.
   */
  void fillRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
    blitter.fillRect(x, y, w, h);
  }

  /** Approximates {@code GraphicsContext.strokeRoundRect} the same way as {@link #fillRoundRect}. */
  void strokeRoundRect(double x, double y, double w, double h, double arcW, double arcH) {
    blitter.strokeRect(x, y, w, h);
  }

  void drawHints(String text, double w, double h) {
    drawHints(text, w, h, 20.0);
  }

  void drawHints(String text, double w, double h, double bottomMargin) {
    drawHints(text, w, h, bottomMargin, null);
  }

  void drawHints(String text, double w, double h, double bottomMargin, MenuStyleSpec style) {
    drawHints(text, w, h, bottomMargin, style, null);
  }

  void drawHints(String text, double w, double h, double bottomMargin, MenuStyleSpec style, MenuLayoutSpec layout) {
    MenuTheme.FontSpec hintFont = resolveHintFontSpec(style);
    MenuTheme.ColorSpec hintColor = parseColorRgba(style != null ? style.hintsColor() : null, theme.getHintColor());
    blitter.setFill(hintColor.r(), hintColor.g(), hintColor.b(), hintColor.a());
    blitter.setFont(hintFont.family(), hintFont.size(), hintFont.bold());
    double textW = blitter.measureTextWidth(text, hintFont.size(), hintFont.bold());
    Double hintsX = layout != null ? layout.hintsX() : null;
    String align = layout != null ? layout.hintsAlign() : "center";
    double x = hintsX != null
        ? w * hintsX - textW / 2.0
        : switch (align == null ? "center" : align.toLowerCase()) {
          case "left" -> 20.0;
          case "right" -> w - textW - 20.0;
          default -> (w - textW) / 2.0;
        };
    blitter.drawText(text, clamp(x, 0, Math.max(0, w - textW)), h - Math.max(0, bottomMargin), hintFont.size(), hintFont.bold());
  }

  void drawCenteredText(String text, double w, double y, MenuTheme.FontSpec font, MenuTheme.ColorSpec color) {
    blitter.setFill(color.r(), color.g(), color.b(), color.a());
    blitter.setFont(font.family(), font.size(), font.bold());
    double textW = blitter.measureTextWidth(text, font.size(), font.bold());
    blitter.drawText(text, (w - textW) / 2, y, font.size(), font.bold());
  }

  /** Text measurement for truncateToWidth/renderHistoryMenu; routes through Blitter2D directly. */
  double measure(String s, MenuTheme.FontSpec font) {
    return blitter.measureTextWidth(s, font.size(), font.bold());
  }

  String truncateToWidth(String text, double maxWidth, MenuTheme.FontSpec font) {
    if (text == null) return "";
    if (maxWidth <= 0) return "";
    if (measure(text, font) <= maxWidth) return text;
    String ellipsis = "...";
    if (measure(ellipsis, font) > maxWidth) return ellipsis;
    int end = text.length();
    while (end > 0) {
      String candidate = text.substring(0, end).stripTrailing() + ellipsis;
      if (measure(candidate, font) <= maxWidth) return candidate;
      end--;
    }
    return ellipsis;
  }

  // --- Shared geometry / parsing helpers (used across list, save/load, settings collaborators) ---

  double resolve(double v, double total) {
    // if v <= 1, treat as fraction of total; otherwise pixels
    return v <= 1.0 ? (total * v) : v;
  }

  double resolveCoordinate(double value, double total) {
    return value <= 1.0 ? total * value : value;
  }

  double resolveLocalCoordinate(double value, double total) {
    return Math.abs(value) <= 1.0 ? total * value : value;
  }

  double resolveSize(double value, double total) {
    return value <= 1.0 ? total * Math.max(0, value) : value;
  }

  double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }

  double clamp(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return min;
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }

  String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  static <T> T firstNonNull(T first, T second) {
    return first != null ? first : second;
  }

  Double parseExtraDouble(MenuItemSpec itemSpec, String key) {
    if (itemSpec == null || itemSpec.extras() == null || key == null) return null;
    String raw = itemSpec.extras().get(key);
    if (raw == null || raw.isBlank()) return null;
    try {
      double value = Double.parseDouble(raw.trim());
      return Double.isFinite(value) ? value : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  boolean parseItemExtraBoolean(MenuItemSpec itemSpec, String key, boolean defaultValue) {
    Boolean parsed = parseItemExtraBooleanNullable(itemSpec, key);
    return parsed != null ? parsed : defaultValue;
  }

  Boolean parseItemExtraBooleanNullable(MenuItemSpec itemSpec, String key) {
    if (itemSpec == null || key == null || itemSpec.extras() == null) return null;
    String raw = itemSpec.extras().get(key);
    if (raw == null || raw.isBlank()) return null;
    return switch (raw.trim().toLowerCase()) {
      case "true", "1", "yes", "y", "on" -> Boolean.TRUE;
      case "false", "0", "no", "n", "off" -> Boolean.FALSE;
      default -> null;
    };
  }

  int parseItemExtraInt(MenuItemSpec itemSpec, String key, int defaultValue) {
    if (itemSpec == null || key == null || itemSpec.extras() == null) return defaultValue;
    String raw = itemSpec.extras().get(key);
    if (raw == null || raw.isBlank()) return defaultValue;
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return defaultValue;
    }
  }

  String extra(MenuItemSpec itemSpec, String key) {
    if (itemSpec == null || key == null || itemSpec.extras() == null) return null;
    return itemSpec.extras().get(key);
  }

  /** Item font resolution for drawMenuList's call chain; returns Blitter2D-ready FontSpec. */
  MenuTheme.FontSpec resolveItemFontSpec(MenuStyleSpec style, MenuItemSpec item) {
    MenuTheme.FontSpec themeFont = theme.getItemFontSpec();
    if (style == null && item == null) return scaledFontSpec(themeFont);
    String family = firstNonBlank(
        item != null ? item.fontFamily() : null,
        style != null ? style.itemFontFamily() : null,
        themeFont.family());
    double size = (item != null && item.fontSize() != null ? item.fontSize()
        : style != null && style.itemFontSize() != null ? style.itemFontSize()
        : themeFont.size()) * activeUiFontScale;
    String weightRaw = firstNonBlank(
        item != null ? item.fontWeight() : null,
        style != null ? style.itemFontWeight() : null);
    boolean bold = parseBoldWeight(weightRaw, themeFont.bold());
    return new MenuTheme.FontSpec(family, size, bold);
  }

  /** Item colour resolution for drawMenuList's call chain; returns Blitter2D-ready ColorSpec. */
  MenuTheme.ColorSpec resolveItemColorSpec(MenuStyleSpec style, boolean selected, boolean enabled) {
    if (!enabled) {
      return parseColorRgba(
          style != null ? style.itemDisabledColor() : null,
          MenuTheme.ColorSpec.rgb255(160, 160, 160, 0.8));
    }
    if (selected) {
      return parseColorRgba(style != null ? style.itemSelectedColor() : null, theme.getItemSelectedColor());
    }
    return parseColorRgba(style != null ? style.itemColor() : null, theme.getItemColor());
  }

  boolean isSectionItem(MenuItemSpec itemSpec) {
    String normalized = normalizedRenderRole(itemSpec);
    if (normalized == null) return false;
    return "section".equals(normalized) || "header".equals(normalized);
  }

  boolean isBodyTextItem(MenuItemSpec itemSpec) {
    String normalized = normalizedRenderRole(itemSpec);
    if (normalized == null) return false;
    return "body".equals(normalized) || "paragraph".equals(normalized) || "text".equals(normalized);
  }

  boolean isNoteTextItem(MenuItemSpec itemSpec) {
    String normalized = normalizedRenderRole(itemSpec);
    if (normalized == null) return false;
    return "note".equals(normalized) || "card".equals(normalized);
  }

  private String normalizedRenderRole(MenuItemSpec itemSpec) {
    if (itemSpec == null || itemSpec.extras() == null) return null;
    String raw = firstNonBlank(itemSpec.extras().get("renderAs"), itemSpec.extras().get("role"));
    if (raw == null) return null;
    return raw.trim().toLowerCase();
  }

  int resolveItemRowSpan(MenuItemSpec itemSpec) {
    if (itemSpec == null || itemSpec.extras() == null) return 1;
    String raw = firstNonBlank(itemSpec.extras().get("rowSpan"), itemSpec.extras().get("rows"));
    if (raw == null || raw.isBlank()) return 1;
    try {
      return Math.max(1, Math.min(32, Integer.parseInt(raw.trim())));
    } catch (NumberFormatException ex) {
      return 1;
    }
  }

  boolean itemContainsPoint(MenuItemSpec itemSpec, Rect rect, double mouseX, double mouseY) {
    if (rect == null) return false;
    if (itemSpec != null && !itemSpec.enabled()) return false;
    if (isSectionItem(itemSpec) || isBodyTextItem(itemSpec) || isNoteTextItem(itemSpec)) return false;
    if (itemSpec != null && itemSpec.extras() != null) {
      String raw = itemSpec.extras().get("boundsPoints");
      if (raw != null && !raw.isBlank()) {
        List<com.jvn.core.ui.BoundsPointCodec.Point> points = com.jvn.core.ui.BoundsPointCodec.parse(raw);
        if (points.size() >= 3) {
          if (com.jvn.core.ui.BoundsPointCodec.containsInRect(points, rect.x(), rect.y(), rect.w(), rect.h(), mouseX, mouseY)) {
            return true;
          }
          return false;
        }
      }
    }
    return rect.contains(mouseX, mouseY);
  }

  Rect resolveItemRect(
      int index,
      int count,
      MenuItemSpec itemSpec,
      MenuItemSpec[] itemSpecs,
      MenuLayoutSpec layout,
      double areaX,
      double areaWidth,
      double h
  ) {
    double yStart = layout != null ? resolve(layout.listYStart(), h) : resolve(theme.getListYStart(), h);
    double lineH = layout != null ? layout.lineHeight() : theme.getLineHeight();
    if (lineH <= 0) lineH = theme.getLineHeight();
    double widthFactor = layout != null ? clamp(layout.listWidthFactor(), 0.1, 1.0) : 1.0;
    double listW = areaWidth * widthFactor;
    Double xCenter = layout != null ? layout.listXCenter() : null;
    double listX;
    if (xCenter != null) {
      listX = areaX + areaWidth * xCenter - listW / 2.0;
      listX = clamp(listX, areaX, areaX + Math.max(0, areaWidth - listW));
    } else {
      String align = layout != null ? layout.textAlign() : "center";
      listX = switch (align == null ? "center" : align.toLowerCase()) {
        case "left" -> areaX;
        case "right" -> areaX + areaWidth - listW;
        default -> areaX + (areaWidth - listW) / 2.0;
      };
    }

    if (itemSpec != null && itemSpec.boundsX() != null && itemSpec.boundsY() != null
        && itemSpec.boundsWidth() != null && itemSpec.boundsHeight() != null) {
      double x = resolveCoordinate(itemSpec.boundsX(), areaWidth) + areaX;
      double y = resolveCoordinate(itemSpec.boundsY(), h);
      double w = resolveSize(itemSpec.boundsWidth(), areaWidth);
      double hh = resolveSize(itemSpec.boundsHeight(), h);
      w = clamp(w, 8, Math.max(8, areaWidth));
      hh = clamp(hh, 8, Math.max(8, h));
      x = clamp(x, areaX, areaX + Math.max(0, areaWidth - w));
      y = clamp(y, 0, Math.max(0, h - hh));
      return new Rect(x, y, w, hh);
    }

    double rowCursor = 0.0;
    if (itemSpecs != null) {
      for (int i = 0; i < index && i < itemSpecs.length; i++) {
        rowCursor += resolveItemRowSpan(itemSpecs[i]);
      }
    } else {
      rowCursor = index;
    }
    int rowSpan = resolveItemRowSpan(itemSpec);
    double itemH = Math.max(24, lineH * rowSpan * 0.92);
    double itemY = (yStart - lineH * 0.70) + rowCursor * lineH;
    return new Rect(listX, itemY, Math.max(1, listW), itemH);
  }

  int hoverIndex(
      int count,
      MenuLayoutSpec layout,
      MenuItemSpec[] itemSpecs,
      double areaX,
      double areaWidth,
      double h,
      double mouseX,
      double mouseY
  ) {
    if (count <= 0) return -1;
    for (int i = 0; i < count; i++) {
      MenuItemSpec item = itemSpecs != null && i < itemSpecs.length ? itemSpecs[i] : null;
      Rect rect = resolveItemRect(i, count, item, itemSpecs, layout, areaX, areaWidth, h);
      if (itemContainsPoint(item, rect, mouseX, mouseY)) return i;
    }
    return -1;
  }

  Rect resolveHistoryContentRect(MenuLayoutSpec layout, double w, double h, MenuTheme.FontSpec titleFont, MenuTheme.FontSpec subtitleFont, String subtitleText) {
    double yStart = layout != null ? resolve(layout.listYStart(), h) : resolve(0.16, h);
    double lineH = layout != null && layout.lineHeight() > 0 ? layout.lineHeight() : 34.0;
    double widthFactor = layout != null ? clamp(layout.listWidthFactor(), 0.1, 1.0) : 0.88;
    double listW = w * widthFactor;
    Double xCenter = layout != null ? layout.listXCenter() : null;
    double listX;
    if (xCenter != null) {
      listX = w * xCenter - listW / 2.0;
      listX = clamp(listX, 24, Math.max(24, w - listW - 24));
    } else {
      String align = layout != null ? layout.textAlign() : "center";
      listX = switch (align == null ? "center" : align.toLowerCase()) {
        case "left" -> 24;
        case "right" -> Math.max(24, w - listW - 24);
        default -> (w - listW) / 2.0;
      };
    }
    double titleY = layout != null && layout.titleY() != null
        ? resolve(layout.titleY(), h)
        : titleFont.size() * 1.2;
    double titleBottom = titleY + titleFont.size() * 0.82;
    if (subtitleText != null && !subtitleText.isBlank()) {
      double gap = layout != null ? layout.subtitleGap() : 12.0;
      titleBottom += gap + subtitleFont.size() * 1.2;
    }
    double top = Math.max(yStart, titleBottom);
    double bottom = h - (layout != null ? Math.max(0.0, layout.hintsBottomMargin()) : 18.0) - 28.0;
    return new Rect(listX, top, Math.max(120, listW), Math.max(lineH, bottom - top));
  }

  /** Shared axis-aligned rect used for hit-testing and layout across all menu-package collaborators. */
  record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }
}
