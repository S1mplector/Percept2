package com.jvn.core.scene2d;

import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unicode-aware portable layout implementation used by {@link Blitter2D}. */
final class TextLayoutEngine {
  private TextLayoutEngine() {}

  static TextLayout2D layout(Blitter2D blitter, TextLayoutRequest request) {
    List<Token> tokens = tokenize(blitter, request);
    List<List<Token>> rawLines = wrap(tokens, request);
    boolean truncated = rawLines.size() > request.maxLines();
    if (truncated) {
      rawLines = new ArrayList<>(rawLines.subList(0, request.maxLines()));
      ellipsize(blitter, rawLines.get(rawLines.size() - 1), request);
    }

    List<TextLayoutLine> lines = new ArrayList<>();
    double y = 0.0;
    double layoutWidth = 0.0;
    for (List<Token> rawLine : rawLines) {
      LineMetrics metrics = metrics(rawLine);
      double lineHeight = Math.max(1.0, metrics.height * request.lineHeightMultiplier());
      double baseline = y + metrics.ascent;
      boolean rtl = isRtl(rawLine, request.direction());
      List<Token> visual = new ArrayList<>(rawLine);
      if (rtl) Collections.reverse(visual);
      double availableWidth = Double.isFinite(request.maxWidth()) ? request.maxWidth() : metrics.width;
      double offset = switch (request.alignment()) {
        case CENTER -> Math.max(0.0, (availableWidth - metrics.width) * 0.5);
        case RIGHT -> Math.max(0.0, availableWidth - metrics.width);
        default -> 0.0;
      };
      List<TextLayoutRun> runs = buildRuns(blitter, visual, offset);
      int start = rawLine.isEmpty() ? 0 : rawLine.get(0).start;
      int end = rawLine.isEmpty() ? start : rawLine.get(rawLine.size() - 1).end;
      lines.add(new TextLayoutLine(
          runs, metrics.width, baseline, metrics.ascent, metrics.descent,
          lineHeight, rtl, start, end));
      y += lineHeight;
      layoutWidth = Math.max(layoutWidth, metrics.width);
    }
    int textLength = request.spans().stream().mapToInt(span -> span.text().length()).sum();
    return new TextLayout2D(lines, layoutWidth, y, truncated, textLength);
  }

  private static List<Token> tokenize(Blitter2D blitter, TextLayoutRequest request) {
    List<Token> result = new ArrayList<>();
    int globalIndex = 0;
    for (TextSpan2D span : request.spans()) {
      TextStyle2D style = span.style().resolved(blitter);
      String[] paragraphs = span.text().split("\\n", -1);
      for (int p = 0; p < paragraphs.length; p++) {
        String paragraph = paragraphs[p];
        if (!paragraph.isEmpty()) {
          BreakIterator breaks = request.wrapMode() == TextWrapMode.CHARACTER
              ? BreakIterator.getCharacterInstance(request.locale())
              : BreakIterator.getLineInstance(request.locale());
          breaks.setText(paragraph);
          int start = breaks.first();
          for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
            String text = paragraph.substring(start, end);
            addTextToken(result, text, style, globalIndex + start, blitter, request);
          }
        }
        globalIndex += paragraph.length();
        if (p < paragraphs.length - 1) {
          result.add(new Token("\n", style, globalIndex, globalIndex + 1, 0, 0, 0, 0, true));
          globalIndex++;
        }
      }
    }
    return result;
  }

  private static Token token(
      Blitter2D blitter, String text, TextStyle2D style, int start, int end
  ) {
    TextFontMetrics2D metrics = blitter.measureTextMetrics(
        text, style.fontFamilies().get(0), style.size(), style.bold());
    return new Token(text, style, start, end, metrics.width(), metrics.ascent(),
        metrics.descent(), metrics.leading(), false);
  }

  private static void addWithCharacterFallback(
      List<Token> result,
      Token measured,
      Blitter2D blitter,
      TextLayoutRequest request
  ) {
    if (request.wrapMode() == TextWrapMode.NONE
        || !Double.isFinite(request.maxWidth())
        || measured.width <= request.maxWidth()) {
      result.add(measured);
      return;
    }
    BreakIterator characters = BreakIterator.getCharacterInstance(request.locale());
    characters.setText(measured.text);
    int start = characters.first();
    for (int end = characters.next(); end != BreakIterator.DONE;
         start = end, end = characters.next()) {
      result.add(token(
          blitter,
          measured.text.substring(start, end),
          measured.style,
          measured.start + start,
          measured.start + end));
    }
  }

  private static void addTextToken(
      List<Token> result,
      String text,
      TextStyle2D style,
      int startIndex,
      Blitter2D blitter,
      TextLayoutRequest request
  ) {
    int contentEnd = text.length();
    while (contentEnd > 0 && Character.isWhitespace(text.charAt(contentEnd - 1))) contentEnd--;
    if (contentEnd > 0 && contentEnd < text.length()) {
      addWithCharacterFallback(
          result,
          token(blitter, text.substring(0, contentEnd), style, startIndex, startIndex + contentEnd),
          blitter,
          request);
      result.add(token(
          blitter,
          text.substring(contentEnd),
          style,
          startIndex + contentEnd,
          startIndex + text.length()));
      return;
    }
    addWithCharacterFallback(
        result,
        token(blitter, text, style, startIndex, startIndex + text.length()),
        blitter,
        request);
  }

  private static List<List<Token>> wrap(List<Token> tokens, TextLayoutRequest request) {
    List<List<Token>> lines = new ArrayList<>();
    List<Token> line = new ArrayList<>();
    double width = 0.0;
    for (Token token : tokens) {
      if (token.newline) {
        lines.add(line);
        line = new ArrayList<>();
        width = 0.0;
        continue;
      }
      boolean wraps = request.wrapMode() != TextWrapMode.NONE
          && Double.isFinite(request.maxWidth())
          && !line.isEmpty()
          && width + token.width > request.maxWidth();
      if (wraps) {
        lines.add(trimTrailingWhitespace(line));
        line = new ArrayList<>();
        width = 0.0;
      }
      if (line.isEmpty() && token.text.isBlank()) continue;
      line.add(token);
      width += token.width;
    }
    if (!line.isEmpty() || lines.isEmpty()) lines.add(trimTrailingWhitespace(line));
    return lines;
  }

  private static List<Token> trimTrailingWhitespace(List<Token> line) {
    int end = line.size();
    while (end > 0 && line.get(end - 1).text.isBlank()) end--;
    return new ArrayList<>(line.subList(0, end));
  }

  private static void ellipsize(
      Blitter2D blitter, List<Token> line, TextLayoutRequest request
  ) {
    if (request.ellipsis().isEmpty() || line.isEmpty()) return;
    TextStyle2D style = line.get(line.size() - 1).style;
    int index = line.get(line.size() - 1).end;
    Token ellipsis = token(blitter, request.ellipsis(), style, index, index);
    double width = line.stream().mapToDouble(token -> token.width).sum();
    while (!line.isEmpty() && Double.isFinite(request.maxWidth())
        && width + ellipsis.width > request.maxWidth()) {
      width -= line.remove(line.size() - 1).width;
    }
    line.add(ellipsis);
  }

  private static LineMetrics metrics(List<Token> tokens) {
    double width = 0, ascent = 0, descent = 0, leading = 0;
    for (Token token : tokens) {
      width += token.width;
      ascent = Math.max(ascent, token.ascent);
      descent = Math.max(descent, token.descent);
      leading = Math.max(leading, token.leading);
    }
    if (tokens.isEmpty()) ascent = 1.0;
    return new LineMetrics(width, ascent, descent, ascent + descent + leading);
  }

  private static boolean isRtl(List<Token> tokens, TextDirection direction) {
    if (direction == TextDirection.RIGHT_TO_LEFT) return true;
    if (direction == TextDirection.LEFT_TO_RIGHT) return false;
    StringBuilder text = new StringBuilder();
    for (Token token : tokens) text.append(token.text);
    return !text.isEmpty() && !new Bidi(text.toString(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        .baseIsLeftToRight();
  }

  private static List<TextLayoutRun> buildRuns(
      Blitter2D blitter, List<Token> tokens, double initialX
  ) {
    List<TextLayoutRun> runs = new ArrayList<>();
    double x = initialX;
    for (Token token : tokens) {
      BreakIterator chars = BreakIterator.getCharacterInstance();
      chars.setText(token.text);
      List<Integer> indices = new ArrayList<>();
      List<Double> offsets = new ArrayList<>();
      indices.add(token.start);
      offsets.add(0.0);
      int localStart = chars.first();
      for (int localEnd = chars.next(); localEnd != BreakIterator.DONE;
           localStart = localEnd, localEnd = chars.next()) {
        String prefix = token.text.substring(0, localEnd);
        double offset = blitter.measureTextMetrics(
            prefix, token.style.fontFamilies().get(0), token.style.size(), token.style.bold()).width();
        indices.add(token.start + localEnd);
        offsets.add(offset);
      }
      runs.add(new TextLayoutRun(
          token.text, token.style, token.start, token.end, x, token.width,
          indices.stream().mapToInt(Integer::intValue).toArray(),
          offsets.stream().mapToDouble(Double::doubleValue).toArray()));
      x += token.width;
    }
    return runs;
  }

  private record Token(
      String text, TextStyle2D style, int start, int end, double width,
      double ascent, double descent, double leading, boolean newline
  ) {}

  private record LineMetrics(double width, double ascent, double descent, double height) {}
}
