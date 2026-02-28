package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

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
}
