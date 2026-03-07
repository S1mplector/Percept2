package com.jvn.audiofx;

import com.jvn.core.nativebridge.NativeLibraryLoader;

public final class AudioFxNativeBridge {
  private static final String LIB_NAME = "jvn_audiofx_native";
  private static final boolean AVAILABLE;
  private static final String DIAGNOSTICS;

  static {
    boolean loaded = false;
    String diagnostics = "uninitialized";
    try {
      loaded = NativeLibraryLoader.load(LIB_NAME);
      diagnostics = loaded ? nBridgeInfo() : "native library not found";
    } catch (Throwable t) {
      loaded = false;
      diagnostics = t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "load failure" : t.getMessage());
    }
    AVAILABLE = loaded;
    DIAGNOSTICS = diagnostics;
  }

  private AudioFxNativeBridge() {}

  public static boolean isAvailable() {
    return AVAILABLE;
  }

  public static String diagnostics() {
    return DIAGNOSTICS;
  }

  public static BeezRenderer createBeezRenderer(int sampleRate) {
    ensureAvailable();
    long handle = nCreateBeezRenderer(sampleRate);
    if (handle == 0L) throw new IllegalStateException("Failed to create native Beez renderer");
    return new BeezRenderer(handle);
  }

  public static AmbienceRenderer createAmbienceRenderer(int sampleRate) {
    ensureAvailable();
    long handle = nCreateAmbienceRenderer(sampleRate);
    if (handle == 0L) throw new IllegalStateException("Failed to create native ambience renderer");
    return new AmbienceRenderer(handle);
  }

  private static void ensureAvailable() {
    if (!AVAILABLE) {
      throw new IllegalStateException("AudioFX native bridge unavailable: " + DIAGNOSTICS);
    }
  }

  private static native String nBridgeInfo();

  private static native long nCreateBeezRenderer(int sampleRate);
  private static native void nConfigureBeez(long handle, String cueId, float intensity, float volume, boolean loop);
  private static native int nRenderBeez(long handle, byte[] pcm, int frames);
  private static native void nSetBeezVolume(long handle, float volume);
  private static native boolean nIsBeezFinished(long handle);
  private static native void nStopBeez(long handle);
  private static native void nDestroyBeezRenderer(long handle);

  private static native long nCreateAmbienceRenderer(int sampleRate);
  private static native void nConfigureAmbience(long handle, String preset, float intensity, float volume, boolean loop);
  private static native int nRenderAmbience(long handle, byte[] pcm, int frames);
  private static native void nSetAmbienceVolume(long handle, float volume);
  private static native boolean nIsAmbienceFinished(long handle);
  private static native void nStopAmbience(long handle);
  private static native void nDestroyAmbienceRenderer(long handle);

  public static final class BeezRenderer implements AutoCloseable {
    private long handle;

    private BeezRenderer(long handle) {
      this.handle = handle;
    }

    public void configure(String cueId, float intensity, float volume, boolean loop) {
      ensureOpen();
      nConfigureBeez(handle, cueId, intensity, volume, loop);
    }

    public int render(byte[] pcm, int frames) {
      ensureOpen();
      return nRenderBeez(handle, pcm, frames);
    }

    public void setVolume(float volume) {
      ensureOpen();
      nSetBeezVolume(handle, volume);
    }

    public boolean isFinished() {
      ensureOpen();
      return nIsBeezFinished(handle);
    }

    public void stop() {
      ensureOpen();
      nStopBeez(handle);
    }

    @Override
    public void close() {
      if (handle == 0L) return;
      nDestroyBeezRenderer(handle);
      handle = 0L;
    }

    private void ensureOpen() {
      if (handle == 0L) throw new IllegalStateException("Native Beez renderer is closed");
    }
  }

  public static final class AmbienceRenderer implements AutoCloseable {
    private long handle;

    private AmbienceRenderer(long handle) {
      this.handle = handle;
    }

    public void configure(String preset, float intensity, float volume, boolean loop) {
      ensureOpen();
      nConfigureAmbience(handle, preset, intensity, volume, loop);
    }

    public int render(byte[] pcm, int frames) {
      ensureOpen();
      return nRenderAmbience(handle, pcm, frames);
    }

    public void setVolume(float volume) {
      ensureOpen();
      nSetAmbienceVolume(handle, volume);
    }

    public boolean isFinished() {
      ensureOpen();
      return nIsAmbienceFinished(handle);
    }

    public void stop() {
      ensureOpen();
      nStopAmbience(handle);
    }

    @Override
    public void close() {
      if (handle == 0L) return;
      nDestroyAmbienceRenderer(handle);
      handle = 0L;
    }

    private void ensureOpen() {
      if (handle == 0L) throw new IllegalStateException("Native ambience renderer is closed");
    }
  }
}
