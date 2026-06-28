package com.jvn.core.vn.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Parses inline markup in dialogue text and converts to TextSpan list.
 * 
 * Markup format:
 * - {shake}text{/shake} - Shaking text
 * - {wave}text{/wave} - Wave effect
 * - {bounce}text{/bounce} - Bouncing text
 * - {color=#FF0000}text{/color} - Colored text
 * - {speed=0.5}text{/speed} - Slower text (0.5x)
 * - {speed=2.0}text{/speed} - Faster text (2x)
 * - {delay=500} - 500ms pause
 * - {w} / {w=1.0} - Pause text reveal until input, or for a timed number of seconds
 * - {nw} / {nw=1.0} - Auto-advance without waiting, optionally after a timed number of seconds
 * - {b}text{/b} - Bold
 * - {i}text{/i} - Italic
 * - {rainbow}text{/rainbow} - Rainbow colors
 * - {ruby=Furigana}Base{/ruby} - Ruby text (Furigana) above the base text
 * 
 * Effects can be nested: {shake}{color=#FF0000}scary{/color}{/shake}
 */
public class TextParser {

  // Pattern to match tags like {shake}, {/shake}, {color=#FF0000}, {delay=500}
  private static final Pattern TAG_PATTERN = Pattern.compile("\\{(/?)([a-zA-Z]+)(?:=([^}]+))?\\}");

  public enum ControlTagType {
    WAIT,
    NOWAIT
  }

  public record ControlTag(ControlTagType type, int position, @Nullable Long durationMs) {}

  /**
   * Parse text with inline markup into a list of TextSpans
   */
  public static List<TextSpan> parse(String text) {
    if (text == null || text.isEmpty()) {
      return List.of(new TextSpan(""));
    }

    List<TextSpan> spans = new ArrayList<>();
    
    // Track current state
    TextEffect currentEffect = TextEffect.NONE;
    String currentColor = null;
    float currentSpeed = 1.0f;
    int pendingDelay = 0;
    String currentRubyText = null;

    Matcher matcher = TAG_PATTERN.matcher(text);
    int lastEnd = 0;

    while (matcher.find()) {
      // Add text before this tag
      if (matcher.start() > lastEnd) {
        String segment = text.substring(lastEnd, matcher.start());
        if (!segment.isEmpty()) {
          spans.add(new TextSpan(segment, currentEffect, currentColor, currentSpeed, pendingDelay, currentRubyText));
          pendingDelay = 0; // Clear delay after use
        }
      }

      String isClosing = matcher.group(1);
      String tagName = matcher.group(2).toLowerCase();
      String tagValue = matcher.group(3);

      if (isClosing.isEmpty()) {
        // Opening tag
        switch (tagName) {
          case "shake" -> currentEffect = TextEffect.SHAKE;
          case "wave" -> currentEffect = TextEffect.WAVE;
          case "bounce" -> currentEffect = TextEffect.BOUNCE;
          case "rainbow" -> currentEffect = TextEffect.RAINBOW;
          case "b", "bold" -> currentEffect = TextEffect.BOLD;
          case "i", "italic" -> currentEffect = TextEffect.ITALIC;
          case "color" -> {
            if (tagValue != null) currentColor = tagValue;
          }
          case "speed" -> {
            if (tagValue != null) {
              try { currentSpeed = Float.parseFloat(tagValue); } 
              catch (NumberFormatException ignored) {}
            }
          }
          case "delay" -> {
            if (tagValue != null) {
              try { pendingDelay = Integer.parseInt(tagValue); } 
              catch (NumberFormatException ignored) {}
            }
          }
          case "ruby" -> {
            if (tagValue != null) currentRubyText = tagValue;
          }
        }
      } else {
        // Closing tag
        switch (tagName) {
          case "shake", "wave", "bounce", "rainbow", "b", "bold", "i", "italic" -> currentEffect = TextEffect.NONE;
          case "color" -> currentColor = null;
          case "speed" -> currentSpeed = 1.0f;
          case "ruby" -> currentRubyText = null;
        }
      }

      lastEnd = matcher.end();
    }

    // Add remaining text
    if (lastEnd < text.length()) {
      String segment = text.substring(lastEnd);
      if (!segment.isEmpty()) {
        spans.add(new TextSpan(segment, currentEffect, currentColor, currentSpeed, pendingDelay, currentRubyText));
      }
    }

    // Ensure at least one span
    if (spans.isEmpty()) {
      spans.add(new TextSpan(""));
    }

    return spans;
  }

  /**
   * Get plain text without markup tags
   */
  public static String stripTags(String text) {
    if (text == null) return "";
    return TAG_PATTERN.matcher(text).replaceAll("");
  }

  /**
   * Calculate total character count excluding markup
   */
  public static int plainLength(String text) {
    return stripTags(text).length();
  }

  /**
   * Extract Ren'Py-style text control tags, positioned in visible-character space.
   * Numeric tag values use Ren'Py semantics and are interpreted as seconds.
   * Suffixes "ms" and "s" are also accepted for convenience.
   */
  public static List<ControlTag> controlTags(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }

    List<ControlTag> controls = new ArrayList<>();
    Matcher matcher = TAG_PATTERN.matcher(text);
    int plainIndex = 0;
    int lastEnd = 0;

    while (matcher.find()) {
      if (matcher.start() > lastEnd) {
        plainIndex += text.substring(lastEnd, matcher.start()).length();
      }

      String isClosing = matcher.group(1);
      if (isClosing.isEmpty()) {
        String tagName = matcher.group(2).toLowerCase();
        String tagValue = matcher.group(3);
        ControlTagType type = switch (tagName) {
          case "w", "wait" -> ControlTagType.WAIT;
          case "nw", "nowait" -> ControlTagType.NOWAIT;
          default -> null;
        };
        if (type != null) {
          controls.add(new ControlTag(type, plainIndex, parseControlDurationMs(tagValue)));
        }
      }

      lastEnd = matcher.end();
    }

    return controls;
  }

  private static @Nullable Long parseControlDurationMs(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String value = raw.trim().toLowerCase();
    double multiplier = 1000.0;
    if (value.endsWith("ms")) {
      multiplier = 1.0;
      value = value.substring(0, value.length() - 2).trim();
    } else if (value.endsWith("s")) {
      value = value.substring(0, value.length() - 1).trim();
    }

    try {
      double parsed = Double.parseDouble(value);
      if (!Double.isFinite(parsed)) return null;
      return Math.max(0L, Math.round(parsed * multiplier));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  /**
   * Get character at reveal index, accounting for speed modifiers.
   * Returns the actual character position and any accumulated delay.
   */
  public static RevealInfo getRevealInfo(List<TextSpan> spans, int revealIndex) {
    int charCount = 0;
    int totalDelay = 0;
    float avgSpeed = 1.0f;

    for (TextSpan span : spans) {
      int spanLen = span.length();
      if (charCount + spanLen > revealIndex) {
        // This span contains the reveal position
        int posInSpan = revealIndex - charCount;
        totalDelay += span.getDelayMs();
        avgSpeed = span.getSpeedMultiplier();
        return new RevealInfo(revealIndex, totalDelay, avgSpeed, span);
      }
      charCount += spanLen;
      totalDelay += span.getDelayMs();
    }

    // Past end
    return new RevealInfo(charCount, totalDelay, 1.0f, null);
  }

  /**
   * Information about a reveal position
   */
  public static class RevealInfo {
    public final int charIndex;
    public final int accumulatedDelayMs;
    public final float speedMultiplier;
    public final TextSpan currentSpan;

    public RevealInfo(int charIndex, int accumulatedDelayMs, float speedMultiplier, TextSpan currentSpan) {
      this.charIndex = charIndex;
      this.accumulatedDelayMs = accumulatedDelayMs;
      this.speedMultiplier = speedMultiplier;
      this.currentSpan = currentSpan;
    }
  }
}
