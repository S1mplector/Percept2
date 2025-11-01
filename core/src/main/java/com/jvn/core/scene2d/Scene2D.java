package com.jvn.core.scene2d;

import com.jvn.core.scene.Scene;

public interface Scene2D extends Scene {
  void render(Blitter2D b, double width, double height);
}
