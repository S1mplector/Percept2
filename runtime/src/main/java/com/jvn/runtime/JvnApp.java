package com.jvn.runtime;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.vn.DemoScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;
import com.jvn.fx.FxLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JvnApp {
  private static final Logger log = LoggerFactory.getLogger(JvnApp.class);

  public static void main(String[] args) {
    ApplicationConfig.Builder builder = ApplicationConfig.builder().title("JVN Runtime").width(960).height(540);

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
    
    // Create engine and load demo VN scenario
    Engine engine = new Engine(cfg);
    engine.start();
    
    log.info("Loading demo VN scenario...");
    VnScenario demoScenario = DemoScenario.createMinimalTest();
    VnScene vnScene = new VnScene(demoScenario);
    engine.scenes().push(vnScene);
    log.info("Demo scenario loaded: {}", demoScenario.getId());

    FxLauncher.launch(engine);
  }
}
