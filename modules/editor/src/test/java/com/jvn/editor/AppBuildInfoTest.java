package com.jvn.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppBuildInfoTest {

  @Test
  void displayVersionLabelUsesFallbackForDev() {
    assertEquals("v0.1 Alpha", AppBuildInfo.displayVersionLabel("dev"));
    assertEquals("v0.1 Alpha", AppBuildInfo.displayVersionLabel("vdev"));
    assertEquals("v0.1 Alpha", AppBuildInfo.displayVersionLabel(""));
  }

  @Test
  void displayVersionLabelMapsSnapshotToAlpha() {
    assertEquals("v0.1 Alpha", AppBuildInfo.displayVersionLabel("0.1-SNAPSHOT"));
  }

  @Test
  void displayVersionLabelKeepsStableVersionPlain() {
    assertEquals("v1.2.3", AppBuildInfo.displayVersionLabel("1.2.3"));
  }
}
