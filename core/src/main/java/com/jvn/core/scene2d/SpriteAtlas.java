package com.jvn.core.scene2d;

import java.util.HashMap;
import java.util.Map;

public class SpriteAtlas {
  public static class Region {
    public final int x, y, w, h;
    public Region(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
  }

  private final String imagePath;
  private final Map<String, Region> regions = new HashMap<>();

  public SpriteAtlas(String imagePath) { this.imagePath = imagePath; }

  public String getImagePath() { return imagePath; }

  public void addRegion(String name, int x, int y, int w, int h) {
    regions.put(name, new Region(x, y, w, h));
  }

  public Region getRegion(String name) { return regions.get(name); }

  public void draw(Blitter2D b, String name, double dx, double dy, double dw, double dh) {
    Region r = regions.get(name);
    if (r == null) return;
    b.drawImageRegion(imagePath, r.x, r.y, r.w, r.h, dx, dy, dw, dh);
  }
}
