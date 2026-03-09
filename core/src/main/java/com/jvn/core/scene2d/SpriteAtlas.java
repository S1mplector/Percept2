package com.jvn.core.scene2d;

import java.util.HashMap;
import java.util.Map;

/**
 * A named-region texture atlas backed by a single image.
 *
 * <p>Unlike {@link SpriteSheet} (which uses a uniform grid), a {@code SpriteAtlas}
 * stores <b>arbitrarily-sized named regions</b> within a single image file.
 * Regions are registered by name and can be drawn individually. This is ideal
 * for packed atlases where sprites have varying dimensions.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SpriteAtlas atlas = new SpriteAtlas("ui/atlas.png");
 * atlas.addRegion("button_normal", 0, 0, 200, 60);
 * atlas.addRegion("button_hover",  0, 60, 200, 60);
 * atlas.draw(blitter, "button_normal", 100, 200, 200, 60);
 * }</pre>
 *
 * @see SpriteSheet
 * @see Sprite2D
 */
public class SpriteAtlas {

  /**
   * Describes a rectangular sub-region within the atlas image.
   * Coordinates are in source-image pixels.
   */
  public static class Region {
    /** Source X offset within the atlas image. */
    public final int x;
    /** Source Y offset within the atlas image. */
    public final int y;
    /** Width of the region in source pixels. */
    public final int w;
    /** Height of the region in source pixels. */
    public final int h;

    /**
     * @param x source X
     * @param y source Y
     * @param w width
     * @param h height
     */
    public Region(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
  }

  /** Asset path to the atlas image. */
  private final String imagePath;

  /** Map of region names to their source rectangles. */
  private final Map<String, Region> regions = new HashMap<>();

  /**
   * Construct an atlas backed by the given image.
   *
   * @param imagePath asset path to the atlas image
   */
  public SpriteAtlas(String imagePath) { this.imagePath = imagePath; }

  /** @return the atlas image asset path */
  public String getImagePath() { return imagePath; }

  /**
   * Register a named region within the atlas.
   *
   * @param name region name (used as the lookup key)
   * @param x    source X within the atlas image
   * @param y    source Y
   * @param w    region width in source pixels
   * @param h    region height in source pixels
   */
  public void addRegion(String name, int x, int y, int w, int h) {
    regions.put(name, new Region(x, y, w, h));
  }

  /**
   * Look up a region by name.
   *
   * @param name the region name
   * @return the region descriptor, or {@code null} if not found
   */
  public Region getRegion(String name) { return regions.get(name); }

  /**
   * Draw a named region from this atlas onto the canvas.
   * If the region name is not found, nothing is drawn.
   *
   * @param b    the blitter
   * @param name region name to draw
   * @param dx   destination X in logical pixels
   * @param dy   destination Y
   * @param dw   destination width
   * @param dh   destination height
   */
  public void draw(Blitter2D b, String name, double dx, double dy, double dw, double dh) {
    Region r = regions.get(name);
    if (r == null) return;
    b.drawImageRegion(imagePath, r.x, r.y, r.w, r.h, dx, dy, dw, dh);
  }
}
