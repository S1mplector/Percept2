package com.jvn.runtime;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnSettingsStore;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.fx.FxLauncher;
import com.jvn.fx.audio.FxAudioService;
import com.jvn.core.audio.AudioFacade;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Paths;

public class JvnApp {
  private static final Logger log = LoggerFactory.getLogger(JvnApp.class);

  public static void main(String[] args) {
    ApplicationConfig.Builder builder = ApplicationConfig.builder().title("JVN Runtime").width(960).height(540);
    String scriptName = "demo.vns"; // default script under game/scripts/
    String locale = "en";
    boolean launchBilliards = false;
    String ui = "fx"; // fx | swing
    String audioBackend = "auto"; // fx | simp3 | auto
    String jesScript = null;
    String assetRoot = null;

    for (int i = 0; i < args.length; i++) {
      String a = args[i];

      String inlineTitle = inlineOptionValue(a, "--title");
      if (inlineTitle != null) {
        builder.title(cleanCliValue(inlineTitle));
        continue;
      }
      String inlineWidth = inlineOptionValue(a, "--width");
      if (inlineWidth != null) {
        builder.width(Integer.parseInt(cleanCliValue(inlineWidth)));
        continue;
      }
      String inlineHeight = inlineOptionValue(a, "--height");
      if (inlineHeight != null) {
        builder.height(Integer.parseInt(cleanCliValue(inlineHeight)));
        continue;
      }
      String inlineScript = inlineOptionValue(a, "--script");
      if (inlineScript != null) {
        scriptName = cleanCliValue(inlineScript);
        continue;
      }
      String inlineLocale = inlineOptionValue(a, "--locale");
      if (inlineLocale != null) {
        locale = cleanCliValue(inlineLocale);
        continue;
      }
      String inlineUi = inlineOptionValue(a, "--ui");
      if (inlineUi != null) {
        ui = cleanCliValue(inlineUi);
        continue;
      }
      String inlineJes = inlineOptionValue(a, "--jes");
      if (inlineJes != null) {
        jesScript = cleanCliValue(inlineJes);
        continue;
      }
      String inlineAudio = inlineOptionValue(a, "--audio");
      if (inlineAudio != null) {
        audioBackend = cleanCliValue(inlineAudio);
        continue;
      }
      String inlineAssets = inlineOptionValue(a, "--assets");
      if (inlineAssets != null) {
        assetRoot = cleanCliValue(inlineAssets);
        continue;
      }

      switch (a) {
        case "--title":
          if (i + 1 < args.length) builder.title(cleanCliValue(args[++i]));
          break;
        case "--width":
          if (i + 1 < args.length) builder.width(Integer.parseInt(cleanCliValue(args[++i])));
          break;
        case "--height":
          if (i + 1 < args.length) builder.height(Integer.parseInt(cleanCliValue(args[++i])));
          break;
        case "--script":
          if (i + 1 < args.length) scriptName = cleanCliValue(args[++i]);
          break;
        case "--locale":
          if (i + 1 < args.length) locale = cleanCliValue(args[++i]);
          break;
        case "--billiards":
          launchBilliards = true;
          break;
        case "--ui":
          if (i + 1 < args.length) ui = cleanCliValue(args[++i]);
          break;
        case "--jes":
          if (i + 1 < args.length) jesScript = cleanCliValue(args[++i]);
          break;
        case "--audio":
          if (i + 1 < args.length) audioBackend = cleanCliValue(args[++i]);
          break;
        case "--assets":
          if (i + 1 < args.length) assetRoot = cleanCliValue(args[++i]);
          break;
        default:
          log.warn("Unknown argument: {}", a);
      }
    }

    ApplicationConfig cfg = builder.build();
    
    // Init localization
    Localization.init(locale, Thread.currentThread().getContextClassLoader());

    AssetManager manager = (assetRoot == null || assetRoot.isBlank())
        ? new ClasspathAssetManager()
        : new OverlayAssetManager(new FilesystemAssetManager(Paths.get(assetRoot)), new ClasspathAssetManager());
    AssetCatalog.setDefaultManager(manager);

    // Log asset availability on startup
    AssetCatalog assets = new AssetCatalog(manager);
    try {
      int img = assets.listImages().size();
      int aud = assets.listAudio().size();
      int scr = assets.listScripts().size();
      int fnt = assets.listFonts().size();
      log.info("Assets -> images={}, audio={}, scripts={}, fonts={}", img, aud, scr, fnt);
    } catch (Exception e) {
      log.warn("Unable to list assets: {}", e.toString());
    }
    
    // Create engine and show scene
    Engine engine = new Engine(cfg);
    engine.setVnInteropFactory(e -> new RuntimeVnInterop(e));
    engine.start();

    if (jesScript != null) {
      loadJes(engine, jesScript);
    } else if (launchBilliards) {
      log.warn("Billiards module is not available; ignoring --billiards flag.");
    } else {
      VnSettings settingsModel = new VnSettingsStore().load();
      VnSaveManager saveManager = new VnSaveManager();
      AudioFacade audio = null;
      if ("simp3".equalsIgnoreCase(audioBackend) || "auto".equalsIgnoreCase(audioBackend)) {
        try {
          Class<?> cls = Class.forName("com.jvn.audio.simp3.Simp3AudioService");
          Object inst = cls.getDeclaredConstructor().newInstance();
          audio = (AudioFacade) inst;
        } catch (Throwable t) {
          // Fallback to FX if adapter not on classpath
          audio = new FxAudioService();
        }
      } else {
        audio = new FxAudioService();
      }
      // Apply user settings to audio backend immediately
      try {
        if (audio != null && settingsModel != null) {
          audio.setBgmVolume(settingsModel.getBgmVolume());
          audio.setSfxVolume(settingsModel.getSfxVolume());
          audio.setVoiceVolume(settingsModel.getVoiceVolume());
        }
      } catch (Exception ignored) {}
      MainMenuScene menu = new MainMenuScene(engine, settingsModel, saveManager, scriptName, audio);
      if (settingsModel != null) {
        engine.setFixedUpdateStepMs(settingsModel.getPhysicsFixedStepMs(), settingsModel.getPhysicsMaxSubSteps());
      }
      engine.scenes().push(menu);
    }

    if ("swing".equalsIgnoreCase(ui)) {
      com.jvn.swing.SwingLauncher.launch(engine);
    } else {
      FxLauncher.launch(engine);
    }
  }

  private static String inlineOptionValue(String arg, String option) {
    if (arg == null || option == null || option.isEmpty()) return null;
    String prefix = option + "=";
    if (!arg.startsWith(prefix)) return null;
    return arg.substring(prefix.length());
  }

  private static String cleanCliValue(String raw) {
    if (raw == null) return "";
    String value = raw.trim();
    while (value.length() >= 2) {
      boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
      boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
      if (!doubleQuoted && !singleQuoted) break;
      value = value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  private static void loadJes(Engine engine, String jesScript) {
    try {
      AssetCatalog cat = new AssetCatalog();
      String[] parts = jesScript.split("[,;]");
      if (parts.length == 1) {
        try (InputStream in = cat.open(AssetType.SCRIPT, jesScript)) {
          JesScene2D scene = JesLoader.load(in);
          new JesVnBridge(engine).attach(scene);
          engine.scenes().push(scene);
        }
      } else {
        java.util.List<InputStream> ins = new java.util.ArrayList<>();
        for (String p : parts) {
          String path = p.trim();
          if (path.isEmpty()) continue;
          ins.add(cat.open(AssetType.SCRIPT, path));
        }
        JesScene2D scene = JesLoader.loadMerged(ins);
        new JesVnBridge(engine).attach(scene);
        engine.scenes().push(scene);
      }
    } catch (Exception e) {
      log.warn("Failed to load JES script '{}': {}. Loading inline sample.", jesScript, e.toString());
      try {
        String sample = "scene \"Sample\" {\n" +
            "  entity \"panel\" {\n" +
            "    component Panel2D { x: 0.2 y: 0.2 w: 1.0 h: 0.6 fill: rgb(0.1,0.6,0.2,0.8) }\n" +
            "  }\n" +
            "}\n";
        var in2 = new ByteArrayInputStream(sample.getBytes());
        var scene = JesLoader.load(in2);
        new JesVnBridge(engine).attach(scene);
        engine.scenes().push(scene);
      } catch (Exception ex) {
        log.warn("Inline JES sample failed: {}", ex.toString());
      }
    }
  }
}
