package com.jvn.core.scene2d;

/**
 * A uniform-grid sprite sheet that maps integer tile indices to source regions.
 *
 * <p>A sprite sheet is a single image containing a grid of equally-sized tiles
 * arranged in rows and columns. Given a tile index, the sheet computes the
 * source rectangle and draws it via {@link Blitter2D#drawImageRegion}.</p>
 *
 * <pre>
 *   ┌────┬────┬────┬────┐
 *   │ 0  │ 1  │ 2  │ 3  │  ← row 0
 *   ├────┼────┼────┼────┤
 *   │ 4  │ 5  │ 6  │ 7  │  ← row 1
 *   └────┴────┴────┴────┘
 *     columns = 4
 * </pre>
 *
 * <p>Tile index {@code i} maps to source pixel coordinates:
 * {@code sx = (i % columns) * tileWidth}, {@code sy = (i / columns) * tileHeight}.</p>
 *
 * @see SpriteAnimation2D
 * @see SpriteAtlas
 */
public class SpriteSheet {

  /** Asset path to the sprite sheet image. */
  private final String imagePath;

  /** Width of a single tile in source pixels. */
  private final int tileWidth;

  /** Height of a single tile in source pixels. */
  private final int tileHeight;

  /** Number of tile columns per row in the sheet. */
  private final int columns;

  /**
   * Construct a sprite sheet descriptor.
   *
   * @param imagePath  asset path to the sheet image
   * @param tileWidth  width of each tile in source pixels
   * @param tileHeight height of each tile in source pixels
   * @param columns    number of columns per row (clamped to ≥ 1)
   */
  public SpriteSheet(String imagePath, int tileWidth, int tileHeight, int columns) {
    this.imagePath = imagePath;
    this.tileWidth = tileWidth;
    this.tileHeight = tileHeight;
    this.columns = Math.max(1, columns);
  }

  /** @return asset path to the sheet image */
  public String getImagePath() { return imagePath; }

  /** @return width of each tile in source pixels */
  public int getTileWidth() { return tileWidth; }

  /** @return height of each tile in source pixels */
  public int getTileHeight() { return tileHeight; }

  /** @return number of tile columns per row */
  public int getColumns() { return columns; }

  /**
   * Draw a single tile from this sheet onto the canvas.
   *
   * @param b     the blitter to draw with
   * @param index 0-based tile index (row-major); negative indices are ignored
   * @param dx    destination X in logical pixels
   * @param dy    destination Y in logical pixels
   * @param dw    destination width
   * @param dh    destination height
   */
  public void drawTile(Blitter2D b, int index, double dx, double dy, double dw, double dh) {
    if (index < 0) return;
    int sx = (index % columns) * tileWidth;
    int sy = (index / columns) * tileHeight;
    b.drawImageRegion(imagePath, sx, sy, tileWidth, tileHeight, dx, dy, dw, dh);
  }
}
