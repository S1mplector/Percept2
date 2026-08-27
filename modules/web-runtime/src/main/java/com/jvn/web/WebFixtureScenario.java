package com.jvn.web;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;
import java.io.IOException;

/**
 * The fixture VN scenario embedded as a compile-time string constant.
 *
 * <p>TeaVM's JS backend does not embed classpath/{@code META-INF} resources into
 * the compiled bundle — {@link com.jvn.core.vn.VnScenarioLoader}'s classpath-based
 * loading (which works correctly on the JVM/desktop) silently fails at runtime in
 * the browser. This class sidesteps that gap for this one small, fixed fixture
 * scenario by embedding its content directly, avoiding any runtime fetch. This is
 * NOT a general solution for loading arbitrary game scripts on web — a real game
 * project's scripts still need a proper (likely async-fetch-based) loading
 * mechanism, which is a separate, future problem.</p>
 */
final class WebFixtureScenario {
  static final String SCRIPT = """
      @scenario web_fixture

      @character lavender "Lavender"
      @charlayer lavender base assets/game/images/characters/lavender/base/lavender_test_sprite_base.png
      @charlayer lavender eyes_neutral assets/game/images/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
      @charlayer lavender mouth_smile assets/game/images/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
      @charpreset lavender happy $base | $eyes_neutral | $mouth_smile

      @background game assets/game/images/bg/game.png

      @label start
      [bg game]
      [show lavender center happy]
      Lavender: Hello from the JVN web scene bootstrap!

      > Wave back -> label_wave
      > Stay quiet -> label_quiet

      @label label_wave
      [end]

      @label label_quiet
      [end]
      """;

  private WebFixtureScenario() {}

  static VnScenario load() throws IOException {
    return new VnScriptParser().parseFromString(SCRIPT);
  }
}
