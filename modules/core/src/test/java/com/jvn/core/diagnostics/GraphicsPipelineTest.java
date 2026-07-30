package com.jvn.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GraphicsPipelineTest {
  private final String originalMode = System.getProperty(GraphicsPipeline.MODE_PROPERTY);
  private final String originalOrder = System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);
  private final String originalOs = System.getProperty("os.name");

  @AfterEach
  void restoreProperties() {
    restore(GraphicsPipeline.MODE_PROPERTY, originalMode);
    restore(GraphicsPipeline.PRISM_ORDER_PROPERTY, originalOrder);
    restore("os.name", originalOs);
  }

  @Test
  void hardwareModePrefersNativePipelinesAndKeepsSoftwareFallback() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "hardware");
    System.setProperty("os.name", "Windows 11");
    System.clearProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);

    assertEquals(GraphicsPipeline.Mode.HARDWARE, GraphicsPipeline.configure());
    assertEquals("d3d,es2,sw", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
    assertTrue(GraphicsPipeline.statusText().startsWith("GPU preferred"));
  }

  @Test
  void softwareModeSelectsCompatibilityPipeline() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "compatibility");
    System.clearProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY);

    assertEquals(GraphicsPipeline.Mode.SOFTWARE, GraphicsPipeline.configure());
    assertEquals("sw", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
  }

  @Test
  void explicitPrismOrderIsNeverOverwritten() {
    System.setProperty(GraphicsPipeline.MODE_PROPERTY, "gpu");
    System.setProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY, "custom");

    GraphicsPipeline.configure();

    assertEquals("custom", System.getProperty(GraphicsPipeline.PRISM_ORDER_PROPERTY));
  }

  private static void restore(String key, String value) {
    if (value == null) System.clearProperty(key);
    else System.setProperty(key, value);
  }
}
