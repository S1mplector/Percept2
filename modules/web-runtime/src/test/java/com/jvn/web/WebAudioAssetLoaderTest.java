package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebAudioAssetLoaderTest {

  @Test
  void resolvesAudioAssetsRelativeToStaticDistribution() {
    assertEquals("assets/game/audio/theme.ogg", WebAudioAssetLoader.resolveUrl("game/audio/theme.ogg"));
    assertEquals("assets/game/audio/theme.ogg", WebAudioAssetLoader.resolveUrl("/game/audio/theme.ogg"));
    assertEquals("assets/game/audio/theme.ogg", WebAudioAssetLoader.resolveUrl("assets/game/audio/theme.ogg"));
    assertEquals("https://cdn.example/theme.ogg", WebAudioAssetLoader.resolveUrl("https://cdn.example/theme.ogg"));
  }
}
