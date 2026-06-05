package com.jvn.core.generalhelp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds Jane's grounded expert corpus from curated knowledge and JVN docs. */
public final class JaneTrainingCorpus {
  private static final int MAX_CHUNK_BODY_CHARS = 6000;
  private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,6})\\s+(.*)$");

  private JaneTrainingCorpus() {}

  public static List<HelpArticle> train(List<HelpArticle> sourceDocs) {
    Map<String, HelpArticle> trained = new LinkedHashMap<>();
    for (HelpArticle article : expertArticles()) {
      trained.put(article.id(), article);
    }
    if (sourceDocs != null) {
      for (HelpArticle article : sourceDocs) {
        if (article == null) continue;
        trained.put(article.id(), article);
        for (HelpArticle chunk : chunkMarkdown(article)) {
          trained.put(chunk.id(), chunk);
        }
      }
    }
    return List.copyOf(trained.values());
  }

  public static List<HelpArticle> expertArticles() {
    List<HelpArticle> articles = new ArrayList<>();
    articles.add(expert(
        "jane-expert-map",
        "Jane Expert Map",
        "High-level map of JVN concepts, languages, tools, and workflows.",
        """
        JVN is Java Vector Nexus, a modular visual novel and 2D animation engine.
        Jane should route questions through the docs before inventing answers.
        Core domains: VNS for visual-novel story scripting, JES for gameplay scenes,
        timeline files for animation data, Puppeteer for keyframe authoring, menu and
        layout DSL files for UI, runtime systems for assets, audio, saves, settings,
        and platform launchers, and editor sidebars for authoring workflows.
        When a user asks how to do something, Jane should name the relevant JVN
        language or tool, recommend the smallest starting doc, and mention the
        likely file types: .vns, .jes, .timeline, .menu, .layout, .style,
        storymap, layered character assets, project manifests, and docs.
        """,
        "JVN, Jane, overview, architecture, VNS, JES, Puppeteer, timeline, menu, layout, workflow"));
    articles.add(expert(
        "jane-vns-expert",
        "VNS Story Scripting Expert",
        "VNS is the story-first visual novel scripting language.",
        """
        VNS is used for dialogue, characters, backgrounds, choices, variables,
        conditions, labels, includes, audio, transitions, text effects, localization,
        rollback, save/load, and VN scene flow. For branching, use choices and labels.
        For reusable files, use include-style project organization. For state, use
        variables and conditions. For presentation, combine character definitions,
        layered images, backgrounds, BGM, voice, transitions, and text effects.
        Relevant docs include VNS overview, VNS language directives, dialogue,
        choices, variables, commands, text formatting, runtime save system, rollback,
        localization, and VNS by example.
        """,
        "VNS, visual novel, dialogue, choice, branch, label, variable, condition, character, background, transition"));
    articles.add(expert(
        "jane-jes-expert",
        "JES Gameplay Scripting Expert",
        "JES is the scene and gameplay scripting language.",
        """
        JES defines 2D scenes with entities, components, tile maps, physics, input,
        AI, RPG systems, UI widgets, timelines, and Java call handlers. Start with
        JES by example for hands-on scene setup. Use components such as sprites,
        labels, panels, character controllers, Ai2D, stats, inventory, equipment,
        physics bodies, buttons, sliders, and tilemaps. Use call handlers to bridge
        JES events into Java, VNS, triggers, physics callbacks, input bindings,
        UI widgets, and AI callbacks.
        """,
        "JES, scene, entity, component, sprite, tilemap, physics, input, AI, RPG, call handler"));
    articles.add(expert(
        "jane-animation-expert",
        "Timeline And Puppeteer Expert",
        "Timelines drive animation and Puppeteer authors them visually.",
        """
        Timeline files describe actions, keyframes, easing, audio cues, labels,
        event cues, and reusable animation clips. Puppeteer is the visual animation
        editor for placing assets, authoring keyframes, previewing motion, exporting
        timeline DSL, and integrating with JES or VNS. Use the Puppeteer editor docs
        for visual authoring and the timeline animation docs for hand coding.
        Typical workflow: prepare assets, open Puppeteer, place sprites, add tracks,
        keyframe transform properties, choose easing, preview, export, then trigger
        the timeline from JES or VNS.
        """,
        "timeline, Puppeteer, animation, keyframe, easing, action, cue, sprite, export"));
    articles.add(expert(
        "jane-ui-menu-expert",
        "Menu And Layout Expert",
        "Menus and UI layouts define screens, dialogue UI, choices, and controls.",
        """
        JVN UI uses menu profiles, screen definitions, layout DSL, style/theme files,
        button layouts, dialogue layouts, choice buttons, textbox action buttons,
        save/load screens, settings screens, help screens, and validation diagnostics.
        Use text-first layout workflow for maintainable UI. Menu registry and
        inheritance help reuse common screen structure. The editor has layout
        launcher, menu flow editor, inspector, asset browser, image attributes,
        image tint, phone assets, and diagnostics panels for UI work.
        """,
        "menu, layout, UI, style, theme, button, dialogue, choice, settings, help screen"));
    articles.add(expert(
        "jane-runtime-assets-expert",
        "Runtime And Asset Workflow Expert",
        "Runtime systems load projects, resolve assets, audio, saves, and platform launchers.",
        """
        Runtime work includes project manifests, project structure, asset management,
        audio system, save system, display settings, VN settings, localization,
        release packaging, deployment, platform launchers, hot reload, and run console
        diagnostics. For assets, keep paths stable, use project folders consistently,
        validate missing files through diagnostics, and package only game projects
        rather than the engine workspace.
        """,
        "runtime, asset, audio, save, settings, localization, package, release, deployment, manifest"));
    articles.add(expert(
        "jane-editor-workflow-expert",
        "Editor Workflow Expert",
        "The editor organizes authoring through sidebars and focused tools.",
        """
        The JVN editor workflow starts from Engine Hub or the editor workspace,
        then uses project explorer for files, story timeline for narrative flow,
        inspector for entity properties, script editor for VNS/JES, VNS diagnostics
        for script issues, label flow map for branch structure, asset browser for
        media, version control for Git workflows, storyboard overlay for framing,
        Puppeteer launcher for animation, and run console for logs and execution.
        """,
        "editor, help center, sidebar, project explorer, inspector, diagnostics, run console, version control"));
    articles.add(expert(
        "jane-debugging-expert",
        "Debugging And Diagnostics Expert",
        "Debug by narrowing the system, checking generated diagnostics, and opening the relevant tool.",
        """
        Debugging JVN projects usually means checking run console output, VNS
        diagnostics, layout validation diagnostics, project health checks, missing
        asset warnings, parser errors, packaging validation, and runtime logs.
        Good answers should ask for the exact error when needed, point to the
        diagnostic source, and suggest the smallest reproducible file or scene.
        """,
        "debug, diagnostic, error, warning, validation, parser, missing asset, run console, project health"));
    return articles;
  }

  public static List<HelpArticle> chunkMarkdown(HelpArticle article) {
    if (article == null || article.body().isBlank()) return List.of();
    List<HelpArticle> chunks = new ArrayList<>();
    String[] lines = article.body().split("\\R", -1);
    String currentHeading = "";
    int currentLevel = 0;
    StringBuilder body = new StringBuilder();
    int chunkIndex = 0;
    for (String line : lines) {
      Matcher heading = HEADING_LINE.matcher(line);
      if (heading.matches()) {
        if (!body.toString().isBlank()) {
          chunks.add(chunk(article, chunkIndex++, currentHeading, currentLevel, body.toString()));
        }
        currentLevel = heading.group(1).length();
        currentHeading = heading.group(2).trim();
        body.setLength(0);
        body.append(line).append('\n');
      } else {
        body.append(line).append('\n');
      }
    }
    if (!body.toString().isBlank()) {
      chunks.add(chunk(article, chunkIndex, currentHeading, currentLevel, body.toString()));
    }
    return chunks;
  }

  private static HelpArticle chunk(
      HelpArticle parent,
      int index,
      String heading,
      int headingLevel,
      String body
  ) {
    String cleanHeading = heading == null || heading.isBlank() ? parent.title() : heading.trim();
    String id = parent.id() + "#chunk-" + index;
    String title = parent.title() + " / " + cleanHeading;
    String compactBody = compact(body, MAX_CHUNK_BODY_CHARS);
    Map<String, String> metadata = new LinkedHashMap<>(parent.metadata());
    metadata.put("kind", "heading-chunk");
    metadata.put("parentId", parent.id());
    metadata.put("heading", cleanHeading);
    metadata.put("headingLevel", String.valueOf(headingLevel));
    metadata.put("openPath", parent.path());
    return new HelpArticle(
        id,
        title,
        firstParagraph(compactBody, parent.summary()),
        parent.path(),
        compactBody,
        metadata);
  }

  private static HelpArticle expert(String id, String title, String summary, String body, String aliases) {
    return new HelpArticle(
        id,
        title,
        summary,
        "jane://training/" + id,
        body + "\n\nAliases: " + aliases,
        Map.of("source", "Jane Training", "kind", "curated", "aliases", aliases));
  }

  private static String firstParagraph(String body, String fallback) {
    if (body == null || body.isBlank()) return fallback == null ? "" : fallback;
    String[] blocks = body.split("\\R\\s*\\R");
    for (String block : blocks) {
      String cleaned = block.replaceAll("(?m)^#{1,6}\\s+", "").replaceAll("\\s+", " ").trim();
      if (!cleaned.isBlank()) return compact(cleaned, 220);
    }
    return fallback == null ? "" : fallback;
  }

  private static String compact(String raw, int maxLength) {
    if (raw == null) return "";
    String normalized = raw.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) return normalized;
    return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
  }
}
