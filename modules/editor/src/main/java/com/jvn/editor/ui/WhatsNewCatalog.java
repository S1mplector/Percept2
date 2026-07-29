package com.jvn.editor.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Versioned release notes shown by the editor after an application update.
 *
 * <p>Each shipped version should add a curated entry here. The display decision deliberately uses
 * the full version label, so moving from an alpha to a beta or stable build is treated as a version
 * change even when the numeric version is unchanged.
 */
public final class WhatsNewCatalog {
  private static final Map<String, Release> RELEASES = createReleases();

  private WhatsNewCatalog() {
  }

  public static boolean shouldShow(String currentVersion, String lastSeenVersion) {
    String current = clean(currentVersion);
    if (current.isBlank()) return false;
    return !current.equalsIgnoreCase(clean(lastSeenVersion));
  }

  public static Release forVersion(String versionLabel) {
    String displayVersion = clean(versionLabel);
    if (displayVersion.isBlank()) displayVersion = "Current version";

    Release exact = RELEASES.get(key(displayVersion));
    if (exact != null) return exact.withVersionLabel(displayVersion);

    Release base = RELEASES.get(key(baseVersion(displayVersion)));
    if (base != null) return base.withVersionLabel(displayVersion);

    return new Release(
        displayVersion,
        "A new version of JVN is ready.",
        List.of(new Section(
            "This release",
            "This build does not include a detailed release-note entry.",
            List.of(
                "Your projects and editor preferences remain available.",
                "You can reopen this screen at any time from Help > What's New."))),
        false);
  }

  private static Map<String, Release> createReleases() {
    Map<String, Release> releases = new LinkedHashMap<>();
    add(releases, new Release(
        "v0.4.3",
        "Broader runtime reach, stronger VNS authoring, and a more dependable play loop.",
        List.of(
            new Section(
                "Web and release workflows",
                "JVN projects are easier to launch and package beyond the desktop editor.",
                List.of(
                    "The web runtime now includes an executable browser bootstrap, application configuration parsing, and a complete runtime session.",
                    "Runtime packaging, project storage, and accessibility behavior are more resilient.",
                    "Console launches now provide a clearer, more consistent path from project actions into a running game.")),
            new Section(
                "VNS authoring tools",
                "The visual-novel workflow has gained faster controls and clearer feedback.",
                List.of(
                    "The VNS tool strip now uses distinct controls for preview, validation, formatting, navigation, and related actions.",
                    "the diagnostics utilities handle timeline issues more safely and offer clearer refresh and toolbar actions.",
                    "Preview launches keep their visible game window and no longer leave hidden preview audio playing.")),
            new Section(
                "Characters and dialogue",
                "Authors have more control over how speakers look and behave.",
                List.of(
                    "Dialogue speakers can define their own persistent colors.",
                    "Characters can keep an authored scale across VNS scenes, with matching editor preview support.",
                    "Expression transitions have been hardened so rapid changes settle on the intended pose.")),
            new Section(
                "Runtime stability and polish",
                "Core playback paths now behave more predictably under real project load.",
                List.of(
                    "Update loops, renderer primitives, audio ownership, and title-menu music startup are more robust.",
                    "The gameplay menu and dialogue presentation have moved closer to full runtime parity.",
                    "Performance HUD metrics now report frame timing and activity more accurately."))),
        true));
    add(releases, new Release(
        "v0.4.2",
        "A more immediate authoring loop, calmer runtime feedback, and sharper editor tools.",
        List.of(
            new Section(
                "Richer Puppeteer previews",
                "The canvas now communicates useful timeline state while you author.",
                List.of(
                    "See playback mode, precise time, selected target, and selected property directly in the preview.",
                    "Track the active expression, layered-expression count, and next keyframe or event at a glance.",
                    "Use the same live readout in compact and focused preview layouts without obscuring anchor placement.")),
            new Section(
                "Improved runtime feedback",
                "Startup and failure states are clearer without interrupting fast runs.",
                List.of(
                    "A themed loading card appears only when preparation takes long enough to notice, then fades after the first frame.",
                    "Runtime errors now emphasize source context, likely cause, recovery actions, and readable technical details.",
                    "Reload, continue, and copy-details shortcuts work from the error screen while gameplay input stays blocked.")),
            new Section(
                "Diagnostics and tool consistency",
                "Sidebar utilities now share a more coherent visual and interaction language.",
                List.of(
                    "VNS diagnostics handle malformed and incomplete source more gracefully and report a broader set of issues.",
                    "Diagnostic actions use clearer icons and more consistent control styling.",
                    "Sidebar tool buttons now follow the same dimensional JVN button treatment used by primary editor actions.")),
            new Section(
                "Version-aware release notes",
                "Update information now appears when it is relevant and remains easy to revisit.",
                List.of(
                    "A dedicated What's New screen opens once whenever the installed JVN version changes.",
                    "Launcher and Editor share the last-seen version, preventing the same release summary from opening twice.",
                    "The Engine Hub announcement bell and screen have been retired in favor of version-specific release notes."))),
        true));
    return Map.copyOf(releases);
  }

  private static void add(Map<String, Release> releases, Release release) {
    releases.put(key(release.versionLabel()), release);
  }

  private static String baseVersion(String versionLabel) {
    String clean = clean(versionLabel);
    int qualifier = clean.indexOf(' ');
    return qualifier < 0 ? clean : clean.substring(0, qualifier);
  }

  private static String key(String versionLabel) {
    return clean(versionLabel).toLowerCase(Locale.ROOT);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  public record Release(
      String versionLabel,
      String summary,
      List<Section> sections,
      boolean curated) {

    public Release {
      versionLabel = clean(versionLabel);
      summary = clean(summary);
      sections = sections == null ? List.of() : List.copyOf(sections);
    }

    private Release withVersionLabel(String displayVersion) {
      return new Release(displayVersion, summary, sections, curated);
    }
  }

  public record Section(String title, String summary, List<String> changes) {
    public Section {
      title = clean(title);
      summary = clean(summary);
      changes = changes == null
          ? List.of()
          : changes.stream().map(WhatsNewCatalog::clean).filter(text -> !text.isBlank()).toList();
    }
  }
}
