package com.jvn.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionRangeTest {
  @Test void supportsCommonManifestRanges() {
    assertTrue(VersionRange.accepts("*", "4.8.0"));
    assertTrue(VersionRange.accepts("1.x", "1.9.2"));
    assertTrue(VersionRange.accepts("1.2.x", "1.2.9"));
    assertTrue(VersionRange.accepts("^1.2.0", "1.8.0"));
    assertTrue(VersionRange.accepts(">=1.2.0 <2.0.0", "1.5.1"));
    assertFalse(VersionRange.accepts("1.2.x", "1.3.0"));
    assertFalse(VersionRange.accepts("^1.2.0", "2.0.0"));
  }
}
