package com.jvn.core.menu;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionBindingProfileStore;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuLayoutSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnSettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SettingsScene implements Scene {
  private static final Logger LOG = LoggerFactory.getLogger(SettingsScene.class);
  private static final String KEY_TEXT_SPEED = "text_speed";
  private static final String KEY_BGM_VOLUME = "bgm_volume";
  private static final String KEY_SFX_VOLUME = "sfx_volume";
  private static final String KEY_VOICE_VOLUME = "voice_volume";
  private static final String KEY_AUTO_PLAY_DELAY = "auto_play_delay";
  private static final String KEY_SKIP_UNREAD = "skip_unread";
  private static final String KEY_SKIP_AFTER_CHOICES = "skip_after_choices";
  private static final String KEY_PHYSICS_FIXED_STEP = "physics_fixed_step";
  private static final String KEY_PHYSICS_MAX_SUBSTEPS = "physics_max_substeps";
  private static final String KEY_PHYSICS_DEFAULT_FRICTION = "physics_default_friction";
  private static final String KEY_INPUT_PROFILE = "input_profile";
  private static final String KEY_BACK = "back";

  private final VnSettings settings;
  private final AudioFacade audio; // optional, to apply volumes live
  private final MenuProfile menuProfile;
  private final MenuScreenSpec menuScreen;
  private final MenuLayoutSpec menuLayout;
  private final List<Row> rows;

  private int selected = 0;
  private ActionBindingProfile bindings;
  private String bindingStatus = "";
  private boolean closeRequested = false;

  private record Row(
      String id,
      String key,
      String label,
      boolean enabled,
      MenuStyleSpec style,
      MenuActionSpec action
  ) {}

  public SettingsScene(VnSettings settings) {
    this(settings, null, null, null);
  }

  public SettingsScene(VnSettings settings, AudioFacade audio) {
    this(settings, audio, null, null);
  }

  public SettingsScene(VnSettings settings, AudioFacade audio, ActionBindingProfile bindings) {
    this(settings, audio, bindings, null);
  }

  SettingsScene(VnSettings settings, AudioFacade audio, ActionBindingProfile bindings, MenuProfile profile) {
    this.settings = settings == null ? new VnSettings() : settings;
    this.audio = audio;
    if (bindings != null) {
      this.bindings = bindings;
    } else if (this.settings.getInputProfileSerialized() != null && !this.settings.getInputProfileSerialized().isBlank()) {
      this.bindings = ActionBindingProfile.deserialize(this.settings.getInputProfileSerialized());
    } else {
      this.bindings = new ActionBindingProfile();
    }
    if (profile == null) {
      MenuProfileLoader.LoadResult menuLoad = MenuProfileLoader.loadWithDiagnostics();
      this.menuProfile = menuLoad.profile();
      for (String warning : menuLoad.diagnostics()) {
        LOG.warn("Menu profile: {}", warning);
      }
    } else {
      this.menuProfile = profile;
    }
    this.menuScreen = menuProfile.screen("settings");
    this.menuLayout = menuProfile.layout(menuScreen.layoutId());
    this.rows = buildRows();
    this.selected = firstSelectableIndex(0);
  }

  public VnSettings model() { return settings; }
  public int itemCount() { return rows.size(); }
  public int getSelected() { return selected; }
  public MenuLayoutSpec getMenuLayout() { return menuLayout; }

  public MenuStyleSpec getStyleForIndex(int idx) {
    Row r = rowAt(idx);
    if (r == null || r.style() == null) return menuProfile.style(menuScreen.defaultStyleId());
    return r.style();
  }

  public MenuItemSpec getMenuItemSpec(int idx) {
    Row row = rowAt(idx);
    if (row == null) return null;
    for (MenuItemSpec item : menuScreen.items()) {
      if (item != null && item.id().equalsIgnoreCase(row.id())) {
        return item;
      }
    }
    return null;
  }

  public boolean isItemEnabled(int idx) {
    Row r = rowAt(idx);
    return r != null && r.enabled();
  }

  public String getDisplayTitle() {
    String t = resolveDisplayText(menuScreen.titleText());
    return (t == null || t.isBlank()) ? Localization.t("settings.title") : t;
  }

  public String getDisplayHints() {
    String t = resolveDisplayText(menuScreen.hintsText());
    if (t == null || t.isBlank()) {
      return "Up/Down, Left/Right, Enter • " + Localization.t("common.back") + ": Esc";
    }
    return t;
  }

  public String[] getDisplayItems() {
    String[] out = new String[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      Row r = rows.get(i);
      String label = (r.label() != null && !r.label().isBlank()) ? r.label() : defaultLabelForKey(r.key());
      String value = valueTextForKey(r.key());
      String text = applyValueTemplate(label, value);
      if (KEY_INPUT_PROFILE.equals(r.key()) && bindingStatus != null && !bindingStatus.isBlank()) {
        text += " • " + bindingStatus;
      }
      out[i] = text;
    }
    return out;
  }

  public boolean hasSliderAt(int idx) {
    Row r = rowAt(idx);
    if (r == null) return false;
    return switch (r.key()) {
      case KEY_TEXT_SPEED,
           KEY_BGM_VOLUME,
           KEY_SFX_VOLUME,
           KEY_VOICE_VOLUME,
           KEY_AUTO_PLAY_DELAY,
           KEY_PHYSICS_FIXED_STEP,
           KEY_PHYSICS_MAX_SUBSTEPS,
           KEY_PHYSICS_DEFAULT_FRICTION -> true;
      default -> false;
    };
  }

  public double sliderValue01At(int idx) {
    Row r = rowAt(idx);
    if (r == null) return 0;
    return switch (r.key()) {
      case KEY_TEXT_SPEED -> clamp01((settings.getTextSpeed() - 10.0) / (120.0 - 10.0));
      case KEY_BGM_VOLUME -> clamp01(settings.getBgmVolume());
      case KEY_SFX_VOLUME -> clamp01(settings.getSfxVolume());
      case KEY_VOICE_VOLUME -> clamp01(settings.getVoiceVolume());
      case KEY_AUTO_PLAY_DELAY -> clamp01((settings.getAutoPlayDelay() - 500.0) / (5000.0 - 500.0));
      case KEY_PHYSICS_FIXED_STEP -> clamp01(settings.getPhysicsFixedStepMs() / 50.0);
      case KEY_PHYSICS_MAX_SUBSTEPS -> clamp01((settings.getPhysicsMaxSubSteps() - 1) / 7.0);
      case KEY_PHYSICS_DEFAULT_FRICTION -> clamp01(settings.getPhysicsDefaultFriction());
      default -> 0.0;
    };
  }

  public void moveSelection(int delta) {
    int count = itemCount();
    if (count <= 0 || delta == 0) return;
    int steps = Math.abs(delta);
    int dir = delta > 0 ? 1 : -1;
    int next = selected;
    for (int i = 0; i < steps; i++) {
      next = nextSelectable(next, dir);
    }
    selected = next;
  }

  public void setSelected(int idx) {
    int count = itemCount();
    if (count <= 0) {
      selected = 0;
      return;
    }
    int clamped = Math.max(0, Math.min(idx, count - 1));
    if (isItemEnabled(clamped)) {
      selected = clamped;
      return;
    }
    selected = firstSelectableIndex(clamped);
  }

  public void adjustCurrent(int delta) {
    Row row = rowAt(selected);
    if (row == null || !row.enabled()) return;
    if (handleAction(row, delta, false)) {
      applyLiveVolumes();
      return;
    }

    switch (row.key()) {
      case KEY_TEXT_SPEED -> settings.setTextSpeed(settings.getTextSpeed() + delta);
      case KEY_BGM_VOLUME -> settings.setBgmVolume(settings.getBgmVolume() + delta * 0.05f);
      case KEY_SFX_VOLUME -> settings.setSfxVolume(settings.getSfxVolume() + delta * 0.05f);
      case KEY_VOICE_VOLUME -> settings.setVoiceVolume(settings.getVoiceVolume() + delta * 0.05f);
      case KEY_AUTO_PLAY_DELAY -> settings.setAutoPlayDelay(settings.getAutoPlayDelay() + delta * 100L);
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(!settings.isSkipUnreadText());
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(!settings.isSkipAfterChoices());
      case KEY_PHYSICS_FIXED_STEP -> settings.setPhysicsFixedStepMs(Math.max(0, settings.getPhysicsFixedStepMs() + delta * 5));
      case KEY_PHYSICS_MAX_SUBSTEPS -> settings.setPhysicsMaxSubSteps(Math.max(1, settings.getPhysicsMaxSubSteps() + delta));
      case KEY_PHYSICS_DEFAULT_FRICTION -> settings.setPhysicsDefaultFriction(settings.getPhysicsDefaultFriction() + delta * 0.05);
      case KEY_INPUT_PROFILE -> {
        if (delta > 0) saveBindingsToDisk();
        else loadBindingsFromDisk();
      }
      case KEY_BACK -> closeRequested = true;
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public void toggleCurrent() {
    Row row = rowAt(selected);
    if (row == null || !row.enabled()) return;
    if (handleAction(row, 0, true)) {
      applyLiveVolumes();
      return;
    }

    switch (row.key()) {
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(!settings.isSkipUnreadText());
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(!settings.isSkipAfterChoices());
      case KEY_INPUT_PROFILE -> loadBindingsFromDisk();
      case KEY_BACK -> closeRequested = true;
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public void setValueByIndex(int idx, double value01) {
    Row row = rowAt(idx);
    if (row == null || !row.enabled()) return;

    if (handleAction(row, value01 > 0.5 ? 1 : -1, false)) {
      applyLiveVolumes();
      return;
    }

    double v = clamp01(value01);
    switch (row.key()) {
      case KEY_TEXT_SPEED -> {
        int min = 10, max = 120;
        int val = (int) Math.round(min + v * (max - min));
        settings.setTextSpeed(val);
      }
      case KEY_BGM_VOLUME -> settings.setBgmVolume((float) v);
      case KEY_SFX_VOLUME -> settings.setSfxVolume((float) v);
      case KEY_VOICE_VOLUME -> settings.setVoiceVolume((float) v);
      case KEY_AUTO_PLAY_DELAY -> {
        long min = 500, max = 5000;
        long val = Math.round(min + v * (max - min));
        settings.setAutoPlayDelay(val);
      }
      case KEY_SKIP_UNREAD -> settings.setSkipUnreadText(v >= 0.5);
      case KEY_SKIP_AFTER_CHOICES -> settings.setSkipAfterChoices(v >= 0.5);
      case KEY_PHYSICS_FIXED_STEP -> settings.setPhysicsFixedStepMs(Math.round(v * 50));
      case KEY_PHYSICS_MAX_SUBSTEPS -> settings.setPhysicsMaxSubSteps(1 + (int) Math.round(v * 7));
      case KEY_PHYSICS_DEFAULT_FRICTION -> settings.setPhysicsDefaultFriction(v);
      case KEY_INPUT_PROFILE -> {
        if (v > 0.5) saveBindingsToDisk(); else loadBindingsFromDisk();
      }
      case KEY_BACK -> {
        if (v > 0.5) closeRequested = true;
      }
      default -> {
      }
    }
    applyLiveVolumes();
  }

  public boolean consumeCloseRequested() {
    boolean out = closeRequested;
    closeRequested = false;
    return out;
  }

  @Override
  public void update(long deltaMs) { }

  @Override
  public void onExit() {
    try {
      new VnSettingsStore().save(settings);
    } catch (Exception ignored) {}
  }

  public String getBindingStatus() { return bindingStatus; }

  public void loadBindingsFromDisk() {
    ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
    bindings = store.load();
    settings.setInputProfileSerialized(bindings.serialize());
    bindingStatus = "Loaded from " + store.getPath();
  }

  public ActionBindingProfile getBindings() { return bindings; }

  private void saveBindingsToDisk() {
    ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
    try {
      store.save(bindings);
      settings.setInputProfileSerialized(bindings.serialize());
      bindingStatus = "Saved to " + store.getPath();
    } catch (Exception e) {
      bindingStatus = "Save failed";
    }
  }

  private List<Row> buildRows() {
    List<Row> out = new ArrayList<>();
    if (!menuScreen.items().isEmpty()) {
      for (MenuItemSpec item : menuScreen.items()) {
        if (item == null) continue;
        String id = normalize(item.id(), "item");
        MenuActionSpec action = item.action() == null ? MenuActionSpec.noop() : item.action();
        String key = canonicalKey(action.target() != null ? action.target() : id);
        MenuStyleSpec style = menuProfile.style(normalize(item.styleId(), menuScreen.defaultStyleId()));
        String label = resolveDisplayText(item.label());
        out.add(new Row(id, key, label, item.enabled(), style, action));
      }
    }
    if (!out.isEmpty()) return out;

    MenuStyleSpec style = menuProfile.style(menuScreen.defaultStyleId());
    out.add(defaultRow(KEY_TEXT_SPEED, style));
    out.add(defaultRow(KEY_BGM_VOLUME, style));
    out.add(defaultRow(KEY_SFX_VOLUME, style));
    out.add(defaultRow(KEY_VOICE_VOLUME, style));
    out.add(defaultRow(KEY_AUTO_PLAY_DELAY, style));
    out.add(defaultRow(KEY_SKIP_UNREAD, style));
    out.add(defaultRow(KEY_SKIP_AFTER_CHOICES, style));
    out.add(defaultRow(KEY_PHYSICS_FIXED_STEP, style));
    out.add(defaultRow(KEY_PHYSICS_MAX_SUBSTEPS, style));
    out.add(defaultRow(KEY_PHYSICS_DEFAULT_FRICTION, style));
    out.add(defaultRow(KEY_INPUT_PROFILE, style));
    return out;
  }

  private Row defaultRow(String key, MenuStyleSpec style) {
    return new Row(key, key, null, true, style, MenuActionSpec.noop());
  }

  private boolean handleAction(Row row, int delta, boolean confirm) {
    MenuActionSpec action = row.action();
    if (action == null) return false;
    return switch (action.type()) {
      case BACK, QUIT -> {
        closeRequested = true;
        yield true;
      }
      case NOOP -> false;
      case SETTINGS_MENU -> false;
      default -> {
        if (confirm) {
          bindingStatus = "Unsupported settings action: " + action.type().name().toLowerCase();
        }
        yield true;
      }
    };
  }

  private int firstSelectableIndex(int fallback) {
    int count = itemCount();
    if (count <= 0) return 0;
    int start = Math.max(0, Math.min(fallback, count - 1));
    if (isItemEnabled(start)) return start;
    for (int i = 1; i <= count; i++) {
      int idx = (start + i) % count;
      if (isItemEnabled(idx)) return idx;
    }
    return start;
  }

  private int nextSelectable(int from, int dir) {
    int count = itemCount();
    if (count <= 0) return 0;
    int start = Math.max(0, Math.min(from, count - 1));
    if (!menuScreen.wrapSelection()) {
      int idx = start;
      while (true) {
        idx += dir;
        if (idx < 0 || idx >= count) return from;
        if (isItemEnabled(idx)) return idx;
      }
    }
    int idx = start;
    for (int i = 0; i < count; i++) {
      idx = (idx + dir + count) % count;
      if (isItemEnabled(idx)) return idx;
    }
    return from;
  }

  private Row rowAt(int idx) {
    if (idx < 0 || idx >= rows.size()) return null;
    return rows.get(idx);
  }

  private void applyLiveVolumes() {
    if (audio != null) {
      audio.setBgmVolume(settings.getBgmVolume());
      audio.setSfxVolume(settings.getSfxVolume());
      audio.setVoiceVolume(settings.getVoiceVolume());
    }
  }

  private String defaultLabelForKey(String key) {
    return switch (key) {
      case KEY_TEXT_SPEED -> Localization.t("settings.text_speed");
      case KEY_BGM_VOLUME -> Localization.t("settings.bgm_volume");
      case KEY_SFX_VOLUME -> Localization.t("settings.sfx_volume");
      case KEY_VOICE_VOLUME -> Localization.t("settings.voice_volume");
      case KEY_AUTO_PLAY_DELAY -> Localization.t("settings.auto_play_delay");
      case KEY_SKIP_UNREAD -> Localization.t("settings.skip_unread");
      case KEY_SKIP_AFTER_CHOICES -> Localization.t("settings.skip_after_choices");
      case KEY_PHYSICS_FIXED_STEP -> "Physics: Fixed Step";
      case KEY_PHYSICS_MAX_SUBSTEPS -> "Physics: Max Substeps";
      case KEY_PHYSICS_DEFAULT_FRICTION -> "Physics: Default Friction";
      case KEY_INPUT_PROFILE -> "Input";
      case KEY_BACK -> Localization.t("common.back");
      default -> titleize(key);
    };
  }

  private String valueTextForKey(String key) {
    return switch (key) {
      case KEY_TEXT_SPEED -> settings.getTextSpeed() + " ms";
      case KEY_BGM_VOLUME -> toPct(settings.getBgmVolume());
      case KEY_SFX_VOLUME -> toPct(settings.getSfxVolume());
      case KEY_VOICE_VOLUME -> toPct(settings.getVoiceVolume());
      case KEY_AUTO_PLAY_DELAY -> settings.getAutoPlayDelay() + " ms";
      case KEY_SKIP_UNREAD -> settings.isSkipUnreadText() ? "ON" : "OFF";
      case KEY_SKIP_AFTER_CHOICES -> settings.isSkipAfterChoices() ? "ON" : "OFF";
      case KEY_PHYSICS_FIXED_STEP -> settings.getPhysicsFixedStepMs() + " ms";
      case KEY_PHYSICS_MAX_SUBSTEPS -> Integer.toString(settings.getPhysicsMaxSubSteps());
      case KEY_PHYSICS_DEFAULT_FRICTION -> toPct((float) settings.getPhysicsDefaultFriction());
      case KEY_INPUT_PROFILE -> "Save/Load (" + settings.getInputProfilePath() + ")";
      default -> null;
    };
  }

  private String applyValueTemplate(String label, String value) {
    if (label == null) label = "";
    if (value == null || value.isBlank()) return label;
    if (label.contains("{value}")) return label.replace("{value}", value);
    if (label.contains("%value%")) return label.replace("%value%", value);
    return label + ": " + value;
  }

  private String resolveDisplayText(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.trim();
    if (value.startsWith("i18n:")) {
      String key = value.substring("i18n:".length()).trim();
      if (!key.isEmpty()) return Localization.t(key);
    }
    return value;
  }

  private String canonicalKey(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String v = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    return switch (v) {
      case "textspeed", "text_speed", "dialogue_speed" -> KEY_TEXT_SPEED;
      case "bgm", "music", "bgm_volume", "music_volume" -> KEY_BGM_VOLUME;
      case "sfx", "sfx_volume", "sound_volume" -> KEY_SFX_VOLUME;
      case "voice", "voice_volume" -> KEY_VOICE_VOLUME;
      case "autodelay", "auto_delay", "autoplay_delay", "auto_play_delay" -> KEY_AUTO_PLAY_DELAY;
      case "skipunread", "skip_unread" -> KEY_SKIP_UNREAD;
      case "skip_after_choices", "skipchoices", "skip_choices" -> KEY_SKIP_AFTER_CHOICES;
      case "physics_fixed_step", "fixed_step", "fixed_step_ms" -> KEY_PHYSICS_FIXED_STEP;
      case "physics_max_substeps", "max_substeps", "max_steps" -> KEY_PHYSICS_MAX_SUBSTEPS;
      case "physics_default_friction", "physics_friction", "default_friction", "friction" -> KEY_PHYSICS_DEFAULT_FRICTION;
      case "input", "input_profile", "bindings", "input_bindings" -> KEY_INPUT_PROFILE;
      case "back", "close", "return" -> KEY_BACK;
      default -> v;
    };
  }

  private String titleize(String raw) {
    String s = normalize(raw, "item").replace('_', ' ').replace('-', ' ');
    if (s.isEmpty()) return "item";
    StringBuilder out = new StringBuilder();
    boolean upper = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
      } else if (upper) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private String toPct(float v) {
    int pct = Math.round(v * 100f);
    return pct + "%";
  }

  private double clamp01(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
    if (v < 0) return 0;
    if (v > 1) return 1;
    return v;
  }

  private String normalize(String v, String def) {
    if (v == null) return def;
    String t = v.trim();
    return t.isEmpty() ? def : t;
  }
}
