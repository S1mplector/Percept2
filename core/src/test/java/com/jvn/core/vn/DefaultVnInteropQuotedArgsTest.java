package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVnInteropQuotedArgsTest {
  @Test
  void supportsQuotedArgsInVarCondAndJavaInterop() {
    VnScenario scenario = new VnScenarioBuilder("quoted_interop")
      .label("start")
      .dialogue("Narrator", "Start")
      .label("ok")
      .dialogue("Narrator", "OK")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("var", "set title \"Final Battle\""), scene);
    assertEquals("Final Battle", scene.getState().getVariable("title"));

    interop.handle(new VnExternalCommand("var", "set subtitle Final Battle"), scene);
    assertEquals("Final Battle", scene.getState().getVariable("subtitle"));

    VnInteropResult cond = interop.handle(
      new VnExternalCommand("cond", "if title == \"Final Battle\" goto ok"),
      scene
    );
    assertFalse(cond.shouldAdvance());
    assertEquals(scenario.getLabelIndex("ok"), scene.getState().getCurrentNodeIndex());

    scene.getState().setCurrentNodeIndex(0);
    interop.handle(new VnExternalCommand("var", "set hp 12"), scene);
    VnInteropResult complexCond = interop.handle(
      new VnExternalCommand("cond", "if (title == \"Final Battle\" && hp >= 10) goto ok"),
      scene
    );
    assertFalse(complexCond.shouldAdvance());
    assertEquals(scenario.getLabelIndex("ok"), scene.getState().getCurrentNodeIndex());

    String payload = Methods.class.getName() + "#join \"hello world\" \"boss fight\"";
    interop.handle(new VnExternalCommand("java", payload), scene);
    assertEquals("java: hello world|boss fight", scene.getState().getHudMessage());
  }

  @Test
  void blocksJavaInteropOutsideAllowedClassPrefixes() {
    VnScenario scenario = new VnScenarioBuilder("quoted_interop")
      .label("start")
      .dialogue("Narrator", "Start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("java", "java.lang.Math#max 1 2"), scene);

    assertEquals("java: class not allowed", scene.getState().getHudMessage());
  }

  @Test
  void togglesVisualizerFlagViaUiInterop() {
    VnScenario scenario = new VnScenarioBuilder("ui_visualizer")
      .label("start")
      .dialogue("Narrator", "Start")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    DefaultVnInterop interop = new DefaultVnInterop();

    interop.handle(new VnExternalCommand("ui", "visualizer on"), scene);
    assertEquals(Boolean.TRUE, scene.getState().getVariable("ui.audioVisualizer"));

    interop.handle(new VnExternalCommand("ui", "visualizer off"), scene);
    assertEquals(Boolean.FALSE, scene.getState().getVariable("ui.audioVisualizer"));

    interop.handle(new VnExternalCommand("ui", "visualizer toggle"), scene);
    Object value = scene.getState().getVariable("ui.audioVisualizer");
    assertTrue(value instanceof Boolean);
    assertTrue((Boolean) value);

    interop.handle(new VnExternalCommand("ui", "visualizer on bars=48"), scene);
    assertEquals(Boolean.TRUE, scene.getState().getVariable("ui.audioVisualizer"));
    assertEquals(48, scene.getState().getVariable("ui.audioVisualizerBars"));

    interop.handle(new VnExternalCommand("ui", "visualizer off bars 24"), scene);
    assertEquals(Boolean.FALSE, scene.getState().getVariable("ui.audioVisualizer"));
    assertEquals(24, scene.getState().getVariable("ui.audioVisualizerBars"));
  }

  public static class Methods {
    public static String join(String a, String b) {
      return a + "|" + b;
    }
  }
}
