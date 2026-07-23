package com.jvn.runtime.accessibility;

import com.jvn.core.accessibility.TextToSpeechService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

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
  private static final boolean AVAILABLE = detectAvailability();

  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "jvn-tts");
    t.setDaemon(true);
    return t;
  });

  private volatile Process activeProcess;
  private final AtomicLong requestGeneration = new AtomicLong();

  @Override
  public void speak(String text, Locale locale) {
    if (!AVAILABLE || text == null || text.isBlank()) return;
    stop();
    long generation = requestGeneration.get();
    executor.submit(() -> {
      if (generation != requestGeneration.get()) return;
      try {
        ProcessBuilder pb = buildCommand(text, locale);
        if (pb == null) return;
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        if (generation != requestGeneration.get()) {
          proc.destroy();
          return;
        }
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
    requestGeneration.incrementAndGet();
    Process proc = activeProcess;
    if (proc != null && proc.isAlive()) {
      proc.destroy();
      activeProcess = null;
    }
  }

  @Override
  public boolean isAvailable() {
    return AVAILABLE;
  }

  private static boolean detectAvailability() {
    if (OS.contains("win")) return executableOnPath("powershell.exe") || executableOnPath("powershell");
    if (OS.contains("mac")) {
      return Files.isExecutable(Path.of("/usr/bin/say")) || executableOnPath("say");
    }
    if (OS.contains("nix") || OS.contains("nux") || OS.contains("linux")) {
      return executableOnPath("espeak-ng");
    }
    return false;
  }

  private static boolean executableOnPath(String executable) {
    String pathValue = System.getenv("PATH");
    if (pathValue == null || pathValue.isBlank()) return false;
    for (String directory : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
      if (directory == null || directory.isBlank()) continue;
      try {
        if (Files.isExecutable(Path.of(directory, executable))) return true;
      } catch (RuntimeException ignored) {
        // Ignore malformed PATH entries and continue checking the remaining entries.
      }
    }
    return false;
  }

  private ProcessBuilder buildCommand(String text, Locale locale) {
    if (OS.contains("win")) {
      String singleQuotedText = text.replace("'", "''");
      String script = String.format(
          "Add-Type -AssemblyName System.Speech; "
          + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
          + "$s.Speak('%s')", singleQuotedText);
      String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
      return new ProcessBuilder("powershell", "-NonInteractive", "-EncodedCommand", encoded);
    } else if (OS.contains("mac")) {
      return new ProcessBuilder("say", text);
    } else {
      // Linux: try espeak-ng
      String lang = locale != null ? locale.getLanguage() : "en";
      return new ProcessBuilder("espeak-ng", "-v", lang, text);
    }
  }
}
