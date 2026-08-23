package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.vn.VnCharacter;

/**
 * Regression coverage for VnRenderer.reportMissingCharacterLayers: when a character's
 * sprite fails to resolve at draw time, the renderer must warn with enough detail to
 * diagnose which character/expression/layer/path was missing, and must not spam the
 * log for repeated renders of the same failure.
 */
class VnRendererMissingLayerWarningTest {

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
  void warnsWithCharacterExpressionLayerIdAndPathForMissingLayer() {
    VnCharacter character = VnCharacter.builder("yui")
        .addExpression("happy", "body.png|mouth_open.png", List.of("body_default", "mouth_open"))
        .build();

    VnRenderer.reportMissingCharacterLayers(character, "yui", "happy", "body.png|mouth_open.png");

    List<ILoggingEvent> warnings = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertEquals(2, warnings.size(), "expected one warning per declared layer");
    String combined = String.join("\n", warnings.stream().map(ILoggingEvent::getFormattedMessage).toList());
    assertTrue(combined.contains("yui"), "warning should mention the character id");
    assertTrue(combined.contains("happy"), "warning should mention the expression");
    assertTrue(combined.contains("body_default"), "warning should mention the layer id");
    assertTrue(combined.contains("mouth_open"), "warning should mention the layer id");
    assertTrue(combined.contains("body.png"), "warning should mention the resolved path");
    assertTrue(combined.contains("mouth_open.png"), "warning should mention the resolved path");
  }

  @Test
  void deduplicatesRepeatedWarningsForTheSameMissingLayer() {
    VnCharacter character = VnCharacter.builder("yui")
        .addExpression("neutral", "body.png", List.of("body_default"))
        .build();

    VnRenderer.reportMissingCharacterLayers(character, "yui", "neutral", "body.png");
    VnRenderer.reportMissingCharacterLayers(character, "yui", "neutral", "body.png");
    VnRenderer.reportMissingCharacterLayers(character, "yui", "neutral", "body.png");

    long warnCount = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    assertEquals(1, warnCount, "repeated renders of the same missing layer must not spam the log");
  }

  @Test
  void fallsBackToImagePathWhenNoLayersAreDeclared() {
    VnCharacter character = VnCharacter.builder("plain")
        .addExpression("neutral", "plain.png")
        .build();

    VnRenderer.reportMissingCharacterLayers(character, "plain", "neutral", "plain.png");

    List<ILoggingEvent> warnings = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).getFormattedMessage().contains("plain.png"));
  }
}
