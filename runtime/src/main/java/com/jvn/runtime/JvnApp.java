package com.jvn.runtime;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.fx.FxLauncher;
import com.jvn.fx.audio.FxAudioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JvnApp {
  private static final Logger log = LoggerFactory.getLogger(JvnApp.class);

  public static void main(String[] args) {
    ApplicationConfig.Builder builder = ApplicationConfig.builder().title("JVN Runtime").width(960).height(540);
    String scriptName = "demo.vns"; // default script under game/scripts/
    String locale = "en";

    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      switch (a) {
        case "--title":
          if (i + 1 < args.length) builder.title(args[++i]);
          break;
        case "--width":
          if (i + 1 < args.length) builder.width(Integer.parseInt(args[++i]));
          break;
        case "--height":
          if (i + 1 < args.length) builder.height(Integer.parseInt(args[++i]));
          break;
        case "--script":
          if (i + 1 < args.length) scriptName = args[++i];
          break;
        case "--locale":
          if (i + 1 < args.length) locale = args[++i];
          break;
        default:
          log.warn("Unknown argument: {}", a);
      }
    }

    ApplicationConfig cfg = builder.build();
    
    // Init localization
    Localization.init(locale, Thread.currentThread().getContextClassLoader());

    // Log asset availability on startup
    AssetCatalog assets = new AssetCatalog();
    try {
      int img = assets.listImages().size();
      int aud = assets.listAudio().size();
      int scr = assets.listScripts().size();
      int fnt = assets.listFonts().size();
      log.info("Assets -> images={}, audio={}, scripts={}, fonts={}", img, aud, scr, fnt);
    } catch (Exception e) {
      log.warn("Unable to list assets: {}", e.toString());
    }
    
    // Create engine and show main menu
    Engine engine = new Engine(cfg);
    engine.start();

    VnSettings settingsModel = new VnSettings();
    VnSaveManager saveManager = new VnSaveManager();
    FxAudioService audio = new FxAudioService();
    MainMenuScene menu = new MainMenuScene(engine, settingsModel, saveManager, scriptName, audio);
    engine.scenes().push(menu);

    FxLauncher.launch(engine);
  }
}
