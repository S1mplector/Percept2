package com.jvn.scenerender.testkit;

import java.util.List;

public record DrawCall(String method, List<Object> args) {
  public DrawCall {
    args = List.copyOf(args);
  }
}
