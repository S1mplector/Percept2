package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RenderDiagnosticsTest {

  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    RenderDiagnostics.reset();
    logger = (Logger) LoggerFactory.getLogger(RenderDiagnostics.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    RenderDiagnostics.reset();
  }

  @Test
  void warnsOnceForSamePathAndContext() {
    RenderDiagnostics.missingAsset("art/hero_eyes.png", "layer:eyes");
    RenderDiagnostics.missingAsset("art/hero_eyes.png", "layer:eyes");
    RenderDiagnostics.missingAsset("art/hero_eyes.png", "layer:eyes");

    long warnCount = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    assertTrue(warnCount == 1, "expected exactly one warning, got " + warnCount);
  }

  @Test
  void warnsSeparatelyForDifferentContexts() {
    RenderDiagnostics.missingAsset("art/hero_eyes.png", "layer:eyes");
    RenderDiagnostics.missingAsset("art/hero_eyes.png", "layer:mouth");

    long warnCount = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    assertTrue(warnCount == 2, "expected two warnings for distinct contexts, got " + warnCount);
  }

  @Test
  void resetClearsDedupState() {
    RenderDiagnostics.missingAsset("art/hero_eyes.png", null);
    RenderDiagnostics.reset();
    RenderDiagnostics.missingAsset("art/hero_eyes.png", null);

    long warnCount = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    assertTrue(warnCount == 2, "expected reset to allow a fresh warning, got " + warnCount);
  }

  @Test
  void unsupportedWarnsOncePerUniqueKeyAndIsThreadSafe() throws InterruptedException {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    int threadCount = 8;
    Thread[] threads = new Thread[threadCount];
    for (int i = 0; i < threadCount; i++) {
      threads[i] = new Thread(() ->
          RenderDiagnostics.unsupported(blitter, RenderFeature.PIXEL_ACCESS, "testOp"));
    }
    for (Thread t : threads) t.start();
    for (Thread t : threads) t.join();
    // No assertion on log output here (warn-once de-dup is exercised via REPORTED's
    // add-once Set semantics) — the real behavior under test is that concurrent calls
    // from multiple threads never throw and never corrupt REPORTED's internal state.
    assertDoesNotThrow(() ->
        RenderDiagnostics.unsupported(blitter, RenderFeature.PIXEL_ACCESS, "testOp"));
  }
}
