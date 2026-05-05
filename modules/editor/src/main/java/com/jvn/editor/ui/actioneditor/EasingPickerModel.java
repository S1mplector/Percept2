package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

final class EasingPickerModel {
  record Option(Easing.Type type, String label, String group, String searchText) {
    EasingSpec defaultSpec() {
      return EasingSpec.of(type);
    }

    boolean matches(String query) {
      if (query == null || query.isBlank()) return true;
      return searchText.contains(query.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final List<Option> OPTIONS = buildOptions();

  private EasingPickerModel() {}

  static List<Option> allOptions() {
    return OPTIONS;
  }

  static Option findByType(Easing.Type type) {
    Easing.Type resolved = type != null ? type : Easing.Type.LINEAR;
    for (Option option : OPTIONS) {
      if (option.type() == resolved) return option;
    }
    return OPTIONS.get(0);
  }

  static List<Option> filter(String query) {
    if (query == null || query.isBlank()) return OPTIONS;
    String needle = query.trim().toLowerCase(Locale.ROOT);
    List<Option> matches = new ArrayList<>();
    for (Option option : OPTIONS) {
      if (option.matches(needle)) matches.add(option);
    }
    return matches;
  }

  private static List<Option> buildOptions() {
    List<Option> options = new ArrayList<>();
    for (Easing.Type type : Easing.Type.values()) {
      String group = categorize(type);
      String label = Easing.displayName(type);
      String searchText = String.join(" ",
          label.toLowerCase(Locale.ROOT),
          Easing.token(type),
          group.toLowerCase(Locale.ROOT));
      options.add(new Option(type, label, group, searchText));
    }
    return Collections.unmodifiableList(options);
  }

  private static String categorize(Easing.Type type) {
    if (type == Easing.Type.CUSTOM || type == Easing.Type.CURVE) return "Custom";
    if (Easing.isNamedCurve(type)) return "Named";
    if (type == Easing.Type.SPRING || type == Easing.Type.DAMPED_SPRING) return "Spring";
    return "Standard";
  }
}
