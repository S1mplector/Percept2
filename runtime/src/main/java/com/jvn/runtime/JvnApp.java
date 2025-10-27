package com.jvn.runtime;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.fx.FxLauncher;
import com.jvn.fx.audio.FxAudioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JvnApp {
  private static final Logger log = LoggerFactory.getLogger(JvnApp.class);

  public static void main(String[] args) {
    ApplicationConfig.Builder builder = ApplicationConfig.builder().title("JVN Runtime").width(960).height(540);
    String scriptName = "demo.vns"; // default script under game/scripts/

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
        default:
          log.warn("Unknown argument: {}", a);
      }
    }

    ApplicationConfig cfg = builder.build();
    
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
    
    // Create engine and load VN scenario (from script if available)
    Engine engine = new Engine(cfg);
    engine.start();

    VnScenario scenario = null;
    try {
      VnScriptParser parser = new VnScriptParser();
      try (var in = assets.open(AssetType.SCRIPT, scriptName)) {
        log.info("Loading VN script: {}", scriptName);
        scenario = parser.parse(in);
      }
    } catch (Exception e) {
      log.warn("Failed to load script '{}': {}. Falling back to built-in demo.", scriptName, e.toString());
    }

    if (scenario == null) {
      // Fallback to built-in demo
      scenario = DemoScenario.createSimpleDemo();
      log.info("Loaded built-in demo scenario: {}", scenario.getId());
    } else {
      log.info("Loaded script scenario: {} (nodes={})", scenario.getId(), scenario.getNodes().size());
    }

    VnScene vnScene = new VnScene(scenario);
    // Wire audio service (JavaFX Media)
    vnScene.setAudioFacade(new FxAudioService());
    engine.scenes().push(vnScene);

    FxLauncher.launch(engine);
  }
}
