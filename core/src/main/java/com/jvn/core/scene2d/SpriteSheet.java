package com.jvn.core.scene2d;

public class SpriteSheet {
  private final String imagePath;
  private final int tileWidth;
  private final int tileHeight;
  private final int columns;

  public SpriteSheet(String imagePath, int tileWidth, int tileHeight, int columns) {
    this.imagePath = imagePath;
    this.tileWidth = tileWidth;
    this.tileHeight = tileHeight;
    this.columns = Math.max(1, columns);
  }

  public String getImagePath() { return imagePath; }
  public int getTileWidth() { return tileWidth; }
  public int getTileHeight() { return tileHeight; }
  public int getColumns() { return columns; }

  public void drawTile(Blitter2D b, int index, double dx, double dy, double dw, double dh) {
    if (index < 0) return;
    int sx = (index % columns) * tileWidth;
    int sy = (index / columns) * tileHeight;
    b.drawImageRegion(imagePath, sx, sy, tileWidth, tileHeight, dx, dy, dw, dh);
  }
}
