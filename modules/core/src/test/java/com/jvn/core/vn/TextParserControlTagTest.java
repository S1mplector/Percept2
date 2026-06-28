package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.text.TextParser;

class TextParserControlTagTest {

  @Test
  void extractsWaitAndNowaitTagsInVisibleCharacterSpace() {
    String text = "Hi{w=0.5} there{nw=250ms}!";

    assertEquals("Hi there!", TextParser.stripTags(text));
    assertEquals(9, TextParser.plainLength(text));

    List<TextParser.ControlTag> tags = TextParser.controlTags(text);
    assertEquals(2, tags.size());
    assertEquals(TextParser.ControlTagType.WAIT, tags.get(0).type());
    assertEquals(2, tags.get(0).position());
    assertEquals(500L, tags.get(0).durationMs());
    assertEquals(TextParser.ControlTagType.NOWAIT, tags.get(1).type());
    assertEquals(8, tags.get(1).position());
    assertEquals(250L, tags.get(1).durationMs());
  }

  @Test
  void acceptsLongFormControlTagAliases() {
    List<TextParser.ControlTag> tags = TextParser.controlTags("{wait}A{nowait=1s}");

    assertEquals(2, tags.size());
    assertEquals(TextParser.ControlTagType.WAIT, tags.get(0).type());
    assertEquals(0, tags.get(0).position());
    assertNull(tags.get(0).durationMs());
    assertEquals(TextParser.ControlTagType.NOWAIT, tags.get(1).type());
    assertEquals(1, tags.get(1).position());
    assertEquals(1000L, tags.get(1).durationMs());
  }
}
