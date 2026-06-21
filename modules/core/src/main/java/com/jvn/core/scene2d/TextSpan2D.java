package com.jvn.core.scene2d;

import java.util.Objects;

public record TextSpan2D(String text, TextStyle2D style) {
  public TextSpan2D {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(style, "style");
  }
}
