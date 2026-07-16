package com.jvn.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudioStateTrackerTest {
  @Test
  void exposesTransportMixAndErrorsInSnapshots() {
    AudioStateTracker tracker = new AudioStateTracker("test", AudioCapabilities.full(false));
    tracker.mix().setMasterVolume(0.8f);
    tracker.loading("music/theme.ogg", true);
    tracker.started(AudioChannel.BGM, "music/theme.ogg");
    tracker.paused();

    AudioSnapshot snapshot = tracker.snapshot(-2, Double.NaN, -1, 3);
    assertEquals("test", snapshot.backendId());
    assertEquals(AudioPlaybackStatus.PAUSED, snapshot.bgmStatus());
    assertEquals("music/theme.ogg", snapshot.bgmTrackId());
    assertTrue(snapshot.bgmLooping());
    assertEquals(0.0, snapshot.bgmPositionSeconds());
    assertEquals(0, snapshot.activeSfxCount());
    assertEquals(3, snapshot.activeVoiceCount());
  }

  @Test
  void listenerFailuresDoNotPreventOtherObservers() {
    AudioStateTracker tracker = new AudioStateTracker("test", AudioCapabilities.basic());
    List<AudioEvent.Type> received = new ArrayList<>();
    tracker.addListener(event -> { throw new IllegalStateException("observer failure"); });
    tracker.addListener(event -> received.add(event.type()));

    tracker.loading("theme", false);
    tracker.error(AudioChannel.BGM, "theme", "decode failed");
    tracker.closed();

    assertEquals(List.of(AudioEvent.Type.LOADING, AudioEvent.Type.ERROR, AudioEvent.Type.CLOSED), received);
    assertEquals(AudioPlaybackStatus.CLOSED, tracker.snapshot(0, 0, 0, 0).bgmStatus());
  }
}
