package com.jvn.core.scene2d;

/**
 * A grid-based 2D tile map entity backed by a {@link SpriteSheet}.
 *
 * <p>{@code TileMap2D} stores a 2D grid of tile indices. Each cell either
 * contains a tile index (≥ 0) referencing a tile in the sprite sheet, or
 * {@code -1} to indicate an empty cell. During rendering, only non-empty
 * cells are drawn.</p>
 *
 * <h2>Grid Layout</h2>
 * <pre>
 *   (0,0)─────────────────────▶ X (cols)
 *     │  tile(0,0)  tile(1,0)  ...
 *     │  tile(0,1)  tile(1,1)  ...
 *     ▼
 *   Y (rows)
 * </pre>
 *
 * <h2>Physics Integration</h2>
 * <p>{@link #buildStaticColliders(com.jvn.core.physics.PhysicsWorld2D)} creates
 * axis-aligned rectangle colliders for every non-empty tile, enabling basic
 * tile-based collision detection.</p>
 *
 * @see SpriteSheet
 * @see Entity2D
 */
public class TileMap2D extends Entity2D {

  /** Sprite sheet providing tile artwork. */
  private final SpriteSheet sheet;

  /** Number of tile columns in the grid. */
  private final int cols;

  /** Number of tile rows in the grid. */
  private final int rows;

  /** 2D array of tile indices. {@code -1} = empty cell. Indexed as {@code tiles[row][col]}. */
  private final int[][] tiles;

  /** Display width of each tile in logical pixels. */
  private final double tileW;

  /** Display height of each tile in logical pixels. */
  private final double tileH;

  /**
   * Construct a tile map with all cells initially empty ({@code -1}).
   *
   * @param sheet     sprite sheet providing tile artwork
   * @param cols      number of columns (clamped to ≥ 1)
   * @param rows      number of rows (clamped to ≥ 1)
   * @param drawTileW display width of each tile
   * @param drawTileH display height of each tile
   */
  public TileMap2D(SpriteSheet sheet, int cols, int rows, double drawTileW, double drawTileH) {
    this.sheet = sheet;
    this.cols = Math.max(1, cols);
    this.rows = Math.max(1, rows);
    this.tileW = drawTileW;
    this.tileH = drawTileH;
    this.tiles = new int[rows][cols];
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) tiles[y][x] = -1;
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Tile access
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set the tile index at the given grid position.
   * Out-of-bounds coordinates are silently ignored.
   *
   * @param x     column index
   * @param y     row index
   * @param index tile index in the sprite sheet (≥ 0), or -1 to clear
   */
  public void setTile(int x, int y, int index) {
    if (x < 0 || y < 0 || x >= cols || y >= rows) return;
    tiles[y][x] = index;
  }

  /**
   * Get the tile index at the given grid position.
   *
   * @param x column index
   * @param y row index
   * @return tile index, or {@code -1} if out of bounds or empty
   */
  public int getTile(int x, int y) {
    if (x < 0 || y < 0 || x >= cols || y >= rows) return -1;
    return tiles[y][x];
  }

  /** @return number of tile columns */
  public int getCols() { return cols; }

  /** @return number of tile rows */
  public int getRows() { return rows; }

  /** @return display width of each tile */
  public double getTileW() { return tileW; }

  /** @return display height of each tile */
  public double getTileH() { return tileH; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Rendering
  // ──────────────────────────────────────────────────────────────────────────

  /** Render all non-empty tiles in row-major order. */
  @Override
  public void render(Blitter2D b) {
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        int idx = tiles[y][x];
        if (idx < 0) continue;
        double dx = x * tileW;
        double dy = y * tileH;
        b.push();
        b.translate(dx, dy);
        sheet.drawTile(b, idx, 0, 0, tileW, tileH);
        b.pop();
      }
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Physics integration
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Create static rectangle colliders in the physics world for every non-empty
   * tile. This is a simple approach suitable for basic tile-based collision.
   *
   * @param world the physics world to populate; {@code null} is a no-op
   */
  public void buildStaticColliders(com.jvn.core.physics.PhysicsWorld2D world) {
    if (world == null) return;
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        if (tiles[y][x] >= 0) {
          world.addStaticRect(new com.jvn.core.math.Rect(x * tileW, y * tileH, tileW, tileH));
        }
      }
    }
  }
}
