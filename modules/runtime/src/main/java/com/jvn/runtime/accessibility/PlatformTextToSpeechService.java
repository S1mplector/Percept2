package com.jvn.runtime.accessibility;

import com.jvn.core.accessibility.TextToSpeechService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Platform TTS implementation.
 *
 * <p>Delegates to the platform speech engine:</p>
 * <ul>
 *   <li>Windows: {@code PowerShell Add-Type / SpeechSynthesizer}</li>
 *   <li>macOS: {@code say} command</li>
 *   <li>Linux: {@code espeak-ng} command</li>
 * </ul>
 *
 * <p>Registered as a {@link java.util.ServiceLoader} provider via
 * {@code META-INF/services/com.jvn.core.accessibility.TextToSpeechService}.</p>
 */
public final class PlatformTextToSpeechService implements TextToSpeechService {

  private static final Logger log = LoggerFactory.getLogger(PlatformTextToSpeechService.class);

  private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-tts");
    t.setDaemon(true);
    return t;
  });

  private volatile Process activeProcess;

  @Override
  public void speak(String text, Locale locale) {
    if (text == null || text.isBlank()) return;
    stop();
    executor.submit(() -> {
      try {
        ProcessBuilder pb = buildCommand(text, locale);
        if (pb == null) return;
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        activeProcess = proc;
        proc.waitFor();
      } catch (Exception e) {
        log.warn("TTS speak failed: {}", e.getMessage());
      } finally {
        activeProcess = null;
      }
    });
  }

  @Override
  public void stop() {
    Process proc = activeProcess;
    if (proc != null && proc.isAlive()) {
      proc.destroy();
      activeProcess = null;
    }
  }

  @Override
  public boolean isAvailable() {
    return OS.contains("win") || OS.contains("mac") || OS.contains("nix")
        || OS.contains("nux") || OS.contains("linux");
  }

  private ProcessBuilder buildCommand(String text, Locale locale) {
    String safe = text.replace("\"", "\\\"");
    if (OS.contains("win")) {
      // Use PowerShell SAPI; quiet on missing assembly
      String script = String.format(
          "Add-Type -AssemblyName System.Speech; "
          + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
          + "$s.Speak(\\\"%s\\\")", safe);
      return new ProcessBuilder("powershell", "-NonInteractive", "-Command", script);
    } else if (OS.contains("mac")) {
      return new ProcessBuilder("say", text);
    } else {
      // Linux: try espeak-ng
      String lang = locale != null ? locale.getLanguage() : "en";
      return new ProcessBuilder("espeak-ng", "-v", lang, text);
    }
  }
}
