package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;

import org.junit.jupiter.api.Test;

class VnPreviewViewTest {

  @Test
  void normalizeAudioBackendValueSupportsRuntimeAliases() {
    assertEquals("auto", VnPreviewView.normalizeAudioBackendValue(null));
    assertEquals("auto", VnPreviewView.normalizeAudioBackendValue(""));
    assertEquals("fx", VnPreviewView.normalizeAudioBackendValue("fx"));
    assertEquals("fx", VnPreviewView.normalizeAudioBackendValue("JavaFX"));
    assertEquals("simp3", VnPreviewView.normalizeAudioBackendValue("simp3"));
    assertEquals("simp3", VnPreviewView.normalizeAudioBackendValue("simp"));
    assertEquals("auto", VnPreviewView.normalizeAudioBackendValue("something-else"));
  }

  @Test
  void resolveAudioBackendReadsRuntimeAudioFromManifest() {
    Properties props = new Properties();
    assertEquals("auto", VnPreviewView.resolveAudioBackend(props));

    props.setProperty("runtime.audio", "fx");
    assertEquals("fx", VnPreviewView.resolveAudioBackend(props));

    props.setProperty("runtime.audio", "simp3");
    assertEquals("simp3", VnPreviewView.resolveAudioBackend(props));

    props.setProperty("runtime.audio", "unknown");
    assertEquals("auto", VnPreviewView.resolveAudioBackend(props));
  }

  @Test
  void cursorLaunchSelectsTheFirstNodeAtOrAfterTheCaret() {
    VnScenario scenario = VnScenario.builder("cursor_launch")
        .addNode(VnNode.builder(VnNodeType.BACKGROUND).sourceLine(3).build())
        .addNode(VnNode.builder(VnNodeType.DIALOGUE).sourceLine(7).build())
        .addNode(VnNode.builder(VnNodeType.CHOICE).sourceLine(11).build())
        .build();

    assertEquals(0, VnPreviewView.findLaunchNodeIndexForSourceLine(scenario, 1));
    assertEquals(1, VnPreviewView.findLaunchNodeIndexForSourceLine(scenario, 4));
    assertEquals(1, VnPreviewView.findLaunchNodeIndexForSourceLine(scenario, 7));
    assertEquals(2, VnPreviewView.findLaunchNodeIndexForSourceLine(scenario, 9));
    assertEquals(2, VnPreviewView.findLaunchNodeIndexForSourceLine(scenario, 20));
  }

  @Test
  void cursorLaunchAtLineEighteenSelectsThe1716Dialogue() throws Exception {
    VnScenario scenario = new VnScriptParser().parseFromString(String.join("\n",
        "@scenario cursor_repro",
        "@character narrator \"\"",
        "@character panel_01_wendi \"\"",
        "@character panel_mags \"\"",
        "@background bg_mags bg.png",
        "",
        "",
        "@label start",
        "",
        "[bg bg_mags]",
        "",
        "",
        "[show panel_01_wendi center neutral]",
        "[show panel_mags center neutral]",
        "narrator:1714",
        "[show panel_01_wendi center neutral]",
        "[show panel_mags center neutral]",
        "narrator:1716"));

    int target = VnPreviewView.findLaunchNodeIndexForSourceLine(scenario, 18);

    assertEquals(18, scenario.getNode(target).getSourceLine());
    assertEquals("1716", scenario.getNode(target).getDialogue().getText());
  }
}
