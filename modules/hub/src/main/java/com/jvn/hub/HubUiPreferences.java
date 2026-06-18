package com.jvn.hub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

final class HubUiPreferences {
  private static final String KEY_UI_MODE = "ui.mode";

  private HubUiPreferences() {
  }

  static Optional<HubUiMode> explicitMode(String[] args) {
    for (String arg : args == null ? new String[0] : args) {
      HubUiMode mode = switch (arg) {
        case "--classic", "--swing-hub" -> HubUiMode.CLASSIC;
        case "--new", "--fx", "--javafx-hub" -> HubUiMode.FX;
        default -> null;
      };
      if (mode != null) return Optional.of(mode);
    }
    HubUiMode propMode = HubUiMode.parse(System.getProperty("jvn.hub.ui"));
    if (propMode != null) return Optional.of(propMode);
    HubUiMode envMode = HubUiMode.parse(System.getenv("JVN_HUB_UI"));
    if (envMode != null) return Optional.of(envMode);
    return Optional.empty();
  }

  static Optional<HubUiMode> savedMode() {
    Path file = preferenceFile();
    if (!Files.isRegularFile(file)) return Optional.empty();
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(file)) {
      props.load(in);
    } catch (IOException ignored) {
      return Optional.empty();
    }
    return Optional.ofNullable(HubUiMode.parse(props.getProperty(KEY_UI_MODE)));
  }

  static void saveMode(HubUiMode mode) {
    if (mode == null) return;
    Path file = preferenceFile();
    Properties props = new Properties();
    props.setProperty(KEY_UI_MODE, mode == HubUiMode.FX ? "fx" : "classic");
    try {
      Files.createDirectories(file.getParent());
      StringBuilder out = new StringBuilder();
      out.append("# JVN Engine Hub preferences\n");
      props.forEach((key, value) -> out.append(key).append('=').append(value).append('\n'));
      Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
    } catch (IOException ignored) {
      // Preference persistence is best-effort; launch can continue.
    }
  }

  static Path preferenceFile() {
    return Path.of(System.getProperty("user.home", "."), ".jvn", "hub.properties")
        .toAbsolutePath()
        .normalize();
  }
}
