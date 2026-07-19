package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.animation.EasingExtensions;

final class PuppeteerEasingCatalog {

    enum Source {
        BUILTIN,
        PRESET,
        PLUGIN
    }

    record Entry(
        String id,
        String label,
        String group,
        Source source,
        EasingSpec spec,
        String searchText
    ) {
        boolean isPreset() {
            return source == Source.PRESET;
        }

        boolean isPlugin() {
            return source == Source.PLUGIN;
        }

        String badge() {
            return isPreset() ? "Preset" : isPlugin() ? "Plugin" : group;
        }

        boolean matches(String query) {
            if (query == null || query.isBlank()) return true;
            return searchText.contains(query.trim().toLowerCase(Locale.ROOT));
        }
    }

    private PuppeteerEasingCatalog() {}

    static List<Entry> buildEntries(List<PuppeteerEasingPresetStore.Preset> presets) {
        List<Entry> entries = new ArrayList<>();
        for (EasingPickerModel.Option option : EasingPickerModel.allOptions()) {
            entries.add(new Entry(
                "builtin:" + Easing.token(option.type()),
                option.label(),
                option.group(),
                Source.BUILTIN,
                option.defaultSpec(),
                buildSearchText(option.label(), option.group(), option.defaultSpec(), false)));
        }
        for (var extension : EasingExtensions.entries()) {
            var easing = extension.extension();
            EasingSpec spec = EasingSpec.extension(extension.id(), java.util.Map.of());
            entries.add(new Entry(
                "plugin:" + extension.id(),
                easing.label(),
                easing.category(),
                Source.PLUGIN,
                spec,
                String.join(" ", easing.label(), easing.description(), easing.category(),
                    extension.id(), spec.toDslString(), "plugin extension")
                    .toLowerCase(Locale.ROOT)));
        }
        if (presets != null && !presets.isEmpty()) {
            List<PuppeteerEasingPresetStore.Preset> sorted = new ArrayList<>(presets);
            sorted.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
            for (PuppeteerEasingPresetStore.Preset preset : sorted) {
                entries.add(new Entry(
                    "preset:" + preset.id(),
                    preset.name(),
                    describeGroup(preset.spec()),
                    Source.PRESET,
                    preset.spec(),
                    buildSearchText(preset.name(), "Preset " + describeGroup(preset.spec()), preset.spec(), true)));
            }
        }
        return Collections.unmodifiableList(entries);
    }

    static List<Entry> filter(List<Entry> entries, String query) {
        if (entries == null || entries.isEmpty()) return List.of();
        if (query == null || query.isBlank()) return entries;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.matches(needle)) matches.add(entry);
        }
        return matches;
    }

    static Entry matchForSpec(List<Entry> entries, EasingSpec spec) {
        if (entries == null || entries.isEmpty()) return null;
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);

        for (Entry entry : entries) {
            if (entry.isPreset() && entry.spec().equals(resolved)) return entry;
        }
        for (Entry entry : entries) {
            if (entry.spec().equals(resolved)) return entry;
        }
        for (Entry entry : entries) {
            if (!entry.isPreset() && !entry.isPlugin()
                && !resolved.isExtension() && entry.spec().getType() == resolved.getType()) return entry;
        }
        return entries.get(0);
    }

    private static String describeGroup(EasingSpec spec) {
        return EasingPickerModel.findByType(spec != null ? spec.getType() : Easing.Type.LINEAR).group();
    }

    private static String buildSearchText(String label, String group, EasingSpec spec, boolean preset) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        String token = Easing.token(resolved.getType()).replace('_', ' ');
        return String.join(" ",
            label.toLowerCase(Locale.ROOT),
            group.toLowerCase(Locale.ROOT),
            token.toLowerCase(Locale.ROOT),
            resolved.toDslString().toLowerCase(Locale.ROOT),
            preset ? "preset project saved" : "builtin default");
    }
}
