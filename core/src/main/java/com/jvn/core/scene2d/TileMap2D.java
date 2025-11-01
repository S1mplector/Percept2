package com.jvn.core.scene2d;

public class TileMap2D extends Entity2D {
  private final SpriteSheet sheet;
  private final int cols;
  private final int rows;
  private final int[][] tiles; // -1 for empty
  private final double tileW;
  private final double tileH;

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

  public void setTile(int x, int y, int index) {
    if (x < 0 || y < 0 || x >= cols || y >= rows) return;
    tiles[y][x] = index;
  }

  public int getTile(int x, int y) {
    if (x < 0 || y < 0 || x >= cols || y >= rows) return -1;
    return tiles[y][x];
  }

  public int getCols() { return cols; }
  public int getRows() { return rows; }
  public double getTileW() { return tileW; }
  public double getTileH() { return tileH; }

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
}
