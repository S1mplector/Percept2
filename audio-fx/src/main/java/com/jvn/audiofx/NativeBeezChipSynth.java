package com.jvn.audiofx;

import com.jvn.audiofx.spi.ChipSynthProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public final class NativeBeezChipSynth implements ChipSynthProvider {
  private static final int SAMPLE_RATE = 44_100;
  private static final int CHANNELS = 2;
  private static final int BYTES_PER_SAMPLE = 2;
  private static final int FRAME_BYTES = CHANNELS * BYTES_PER_SAMPLE;
  private static final int BUFFER_FRAMES = 1024;

  private final Object lock = new Object();

  private volatile boolean running;
  private volatile String cueId = "blip";
  private volatile float intensity = 0.85f;
  private volatile float volume = 0.7f;
  private volatile boolean loop;

  private AudioFxNativeBridge.BeezRenderer renderer;
  private SourceDataLine line;
  private Thread worker;
  private long sessionId;
  private long rendererSessionId;
  private long lineSessionId;

  @Override
  public String id() {
    return "native-beez";
  }

  @Override
  public void play(String newCueId, float newIntensity, float newVolume, boolean newLoop) {
    synchronized (lock) {
      cueId = (newCueId == null || newCueId.isBlank()) ? "blip" : newCueId;
      intensity = clamp01(newIntensity);
      volume = clamp01(newVolume);
      loop = newLoop;
      if (!running) {
        sessionId++;
      }
      long targetSession = sessionId;
      if (renderer == null) {
        renderer = AudioFxNativeBridge.createBeezRenderer(SAMPLE_RATE);
        rendererSessionId = targetSession;
      }
      renderer.configure(cueId, intensity, volume, loop);
      if (running) return;
      running = true;
      worker = new Thread(() -> runLoop(targetSession), "audiofx-native-beez");
      worker.setDaemon(true);
      worker.start();
    }
  }

  @Override
  public void stop() {
    final Thread threadToJoin;
    final SourceDataLine lineToClose;
    final long stoppingSession;
    synchronized (lock) {
      stoppingSession = sessionId;
      running = false;
      if (renderer != null) renderer.stop();
      threadToJoin = worker;
      lineToClose = detachLineLocked(stoppingSession);
    }
    closeLine(lineToClose);
    if (joinWorker(threadToJoin)) {
      closeRenderer(detachRenderer(stoppingSession));
    }
  }

  @Override
  public void setVolume(float newVolume) {
    synchronized (lock) {
      volume = clamp01(newVolume);
      if (renderer != null) {
        renderer.setVolume(volume);
      }
    }
  }

  private void runLoop(long session) {
    byte[] pcm = new byte[BUFFER_FRAMES * FRAME_BYTES];
    try {
      AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
      SourceDataLine openedLine = (SourceDataLine) AudioSystem.getLine(info);
      openedLine.open(format, pcm.length * 4);
      openedLine.start();
      synchronized (lock) {
        if (session == sessionId && running) {
          line = openedLine;
          lineSessionId = session;
        } else {
          closeLine(openedLine);
          return;
        }
      }

      while (running) {
        AudioFxNativeBridge.BeezRenderer active;
        int written;
        boolean finished;
        synchronized (lock) {
          active = renderer;
          if (active == null) break;
          written = active.render(pcm, BUFFER_FRAMES);
          finished = !loop && active.isFinished();
        }
        if (active == null) break;
        if (written <= 0) break;
        SourceDataLine activeLine = line;
        if (activeLine != null) {
          activeLine.write(pcm, 0, written);
        }
        if (finished) {
          break;
        }
      }
    } catch (Exception ignored) {
    } finally {
      synchronized (lock) {
        if (worker == Thread.currentThread()) {
          worker = null;
        }
        if (session == sessionId) {
          running = false;
        }
      }
      closeLine(detachLine(session));
      closeRenderer(detachRenderer(session));
    }
  }

  private SourceDataLine detachLine(long session) {
    synchronized (lock) {
      return detachLineLocked(session);
    }
  }

  private SourceDataLine detachLineLocked(long session) {
    if (line == null || lineSessionId != session) return null;
    SourceDataLine detached = line;
    line = null;
    lineSessionId = 0L;
    return detached;
  }

  private AudioFxNativeBridge.BeezRenderer detachRenderer(long session) {
    synchronized (lock) {
      if (renderer == null || rendererSessionId != session) return null;
      AudioFxNativeBridge.BeezRenderer detached = renderer;
      renderer = null;
      rendererSessionId = 0L;
      return detached;
    }
  }

  private boolean joinWorker(Thread threadToJoin) {
    if (threadToJoin == null || threadToJoin == Thread.currentThread()) return true;
    try {
      threadToJoin.join(1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return !threadToJoin.isAlive();
  }

  private void closeLine(SourceDataLine lineToClose) {
    if (lineToClose == null) return;
    try {
      lineToClose.stop();
    } catch (Exception ignored) {
    }
    try {
      lineToClose.flush();
    } catch (Exception ignored) {
    }
    try {
      lineToClose.close();
    } catch (Exception ignored) {
    }
  }

  private void closeRenderer(AudioFxNativeBridge.BeezRenderer rendererToClose) {
    if (rendererToClose == null) return;
    try {
      rendererToClose.close();
    } catch (Exception ignored) {
    }
  }

  private static float clamp01(float value) {
    if (value < 0f) return 0f;
    if (value > 1f) return 1f;
    return value;
  }
}
