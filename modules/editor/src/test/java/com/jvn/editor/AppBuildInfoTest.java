package com.jvn.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppBuildInfoTest {

  @Test
  void displayVersionLabelUsesFallbackForDev() {
    assertEquals("v0.3.1", AppBuildInfo.displayVersionLabel("dev"));
    assertEquals("v0.3.1", AppBuildInfo.displayVersionLabel("vdev"));
    assertEquals("v0.3.1", AppBuildInfo.displayVersionLabel(""));
  }

  @Test
  void displayVersionLabelMapsSnapshotToAlpha() {
    assertEquals("v0.3.1 Alpha", AppBuildInfo.displayVersionLabel("0.3.1-SNAPSHOT"));
  }

  @Test
  void displayVersionLabelKeepsStableVersionPlain() {
    assertEquals("v1.2.3", AppBuildInfo.displayVersionLabel("1.2.3"));
  }
}
