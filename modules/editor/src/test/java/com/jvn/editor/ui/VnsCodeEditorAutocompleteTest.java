package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class VnsCodeEditorAutocompleteTest {

  @Test
  void contextualSuggestionsExposeShowNamedOptions() {
    List<CodeAutoCompleter.Suggestion> suggestions =
        VnsCodeEditor.contextualCommandSuggestions("[show lavender po", "[show lavender po".length(), "po");

    assertTrue(suggestions.stream().anyMatch(s -> "pos=".equals(s.insert)));
    assertTrue(suggestions.stream().anyMatch(s -> "pos=center".equals(s.insert)));
  }

  @Test
  void contextualSuggestionsExposeMoveEasingValues() {
    List<CodeAutoCompleter.Suggestion> suggestions =
        VnsCodeEditor.contextualCommandSuggestions("[move lavender ease=", "[move lavender ease=".length(), "ease=");

    assertTrue(suggestions.stream().anyMatch(s -> "ease=linear".equals(s.insert)));
    assertTrue(suggestions.stream().anyMatch(s -> "ease=easeInOut".equals(s.insert)));
  }

  @Test
  void contextualSuggestionsExposeTransitionTypes() {
    List<CodeAutoCompleter.Suggestion> suggestions =
        VnsCodeEditor.contextualCommandSuggestions("[transition type=cr", "[transition type=cr".length(), "type=cr");

    assertTrue(suggestions.stream().anyMatch(s -> "type=crossfade".equals(s.insert)));
  }

  @Test
  void contextualSuggestionsIgnoreClosedCommands() {
    List<CodeAutoCompleter.Suggestion> suggestions =
        VnsCodeEditor.contextualCommandSuggestions("[show lavender pos=center] po", "[show lavender pos=center] po".length(), "po");

    assertTrue(suggestions.isEmpty());
  }

  @Test
  void contextualSuggestionsExposeParticlePresets() {
    List<CodeAutoCompleter.Suggestion> suggestions =
        VnsCodeEditor.contextualCommandSuggestions("[particles preset=r", "[particles preset=r".length(), "preset=r");

    assertTrue(suggestions.stream().anyMatch(s -> "preset=rain".equals(s.insert)));
  }

  @Test
  void hoverDocForShowExplainsExpressionCrossfadeDefaults() {
    String doc = VnsCodeEditor.commandHoverDoc("show");

    assertTrue(doc.contains("expr="));
    assertTrue(doc.contains("180ms"));
    assertTrue(doc.contains("dur=0"));
  }

  @Test
  void hoverDocForMoveExplainsExpressionCrossfadeDefaults() {
    String doc = VnsCodeEditor.commandHoverDoc("move");

    assertTrue(doc.contains("expr="));
    assertTrue(doc.contains("180ms"));
    assertTrue(doc.contains("dur=0"));
    assertTrue(doc.contains("dur=500"));
  }

  @Test
  void timelineFoldScannerAcceptsInlineOpeningBrace() {
    String source = """
        [character john expression neutral]
        timeline {
          parallel {
            move "john_neutral_body_default" {
              x: 1.300779
              dur: 100
            }
          }
        }
        """;

    assertTrue(VnsCodeEditor.hasTimelineFoldRegionStartingAt(source, 1));
  }

  @Test
  void timelineFoldScannerAcceptsNextLineOpeningBrace() {
    String source = """
        [character john expression neutral]
        timeline
        {
          wait 100
        }
        """;

    assertTrue(VnsCodeEditor.hasTimelineFoldRegionStartingAt(source, 1));
  }
}
