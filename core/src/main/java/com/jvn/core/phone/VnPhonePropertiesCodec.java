package com.jvn.core.phone;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;

/**
 * Codec for project-seeded and runtime-persisted phone data.
 */
public final class VnPhonePropertiesCodec {
  public static final String DEFAULT_CONFIG_PATH = "phone/phone.properties";
  public static final String DIRECT_CONFIG_PATH = "config/phone/phone.properties";

  private static final Pattern CONTACT_KEY_PATTERN = Pattern.compile("^contact\\.([^.]+)\\..+$");
  private static final Pattern CHAT_KEY_PATTERN = Pattern.compile("^chat\\.([^.]+)\\..+$");
  private static final Pattern MESSAGE_KEY_PATTERN = Pattern.compile("^chat\\.([^.]+)\\.message\\.([^.]+)\\..+$");
  private static final Pattern PHONE_APP_KEY_PATTERN = Pattern.compile("^phoneapp\\.([^.]+)\\..+$");
  private static final Pattern CALL_KEY_PATTERN = Pattern.compile("^call\\.([^.]+)\\..+$");

  private VnPhonePropertiesCodec() {
  }

  public static VnPhoneData load(InputStream in) throws IOException {
    Properties props = new Properties();
    if (in != null) props.load(in);
    return fromProperties(props);
  }

  public static VnPhoneData loadFromString(String text) throws IOException {
    Properties props = new Properties();
    if (text != null && !text.isBlank()) {
      props.load(new StringReader(text));
    }
    return fromProperties(props);
  }

  public static VnPhoneData loadSeedFromAssets() {
    AssetCatalog assets = new AssetCatalog();
    try {
      if (assets.url(AssetType.CONFIG, DEFAULT_CONFIG_PATH) != null) {
        try (InputStream in = assets.open(AssetType.CONFIG, DEFAULT_CONFIG_PATH)) {
          return load(in);
        }
      }
      if (assets.url(AssetType.OTHER, DIRECT_CONFIG_PATH) != null) {
        try (InputStream in = assets.open(AssetType.OTHER, DIRECT_CONFIG_PATH)) {
          return load(in);
        }
      }
    } catch (Exception ignored) {
    }
    return new VnPhoneData();
  }

  public static VnPhoneData fromProperties(Properties props) {
    VnPhoneData data = new VnPhoneData();
    if (props == null || props.isEmpty()) return data;

    data.setTitle(props.getProperty("app.title"));
    data.setSubtitle(props.getProperty("app.subtitle"));
    data.setWallpaperPath(props.getProperty("app.wallpaper"));
    data.setAccentColor(props.getProperty("app.accent"));
    data.setSurfaceColor(props.getProperty("app.surface"));
    data.setIncomingBubbleColor(props.getProperty("app.bubbleIncoming"));
    data.setOutgoingBubbleColor(props.getProperty("app.bubbleOutgoing"));
    data.setSkinId(firstNonBlank(
        props.getProperty("app.skin"),
        props.getProperty("app.style"),
        props.getProperty("app.theme")));
    data.setSkinBackgroundPath(props.getProperty("app.skin.background"));
    data.setSkinTopBarPath(props.getProperty("app.skin.topBar"));
    data.setSkinBottomBarPath(props.getProperty("app.skin.bottomBar"));
    data.setSkinMessageFieldPath(props.getProperty("app.skin.messageField"));
    data.setSkinNavLeadingPath(props.getProperty("app.skin.nav.leading"));
    data.setSkinNavTrailingPrimaryPath(props.getProperty("app.skin.nav.trailingPrimary"));
    data.setSkinNavTrailingSecondaryPath(props.getProperty("app.skin.nav.trailingSecondary"));
    data.setSkinComposerLeadingPath(props.getProperty("app.skin.composer.leading"));
    data.setSkinComposerTrailingPrimaryPath(props.getProperty("app.skin.composer.trailingPrimary"));
    data.setSkinComposerTrailingSecondaryPath(props.getProperty("app.skin.composer.trailingSecondary"));
    data.setSkinStatusBackdropPath(props.getProperty("app.skin.statusBackdrop"));
    data.setSkinStatusIconPath(props.getProperty("app.skin.statusIcon"));
    data.setSkinFloatingActionPath(props.getProperty("app.skin.floatingAction"));
    data.setIncomingBubbleImagePath(firstNonBlank(
        props.getProperty("app.skin.bubbleIncoming"),
        props.getProperty("app.bubbleIncomingImage")));
    data.setOutgoingBubbleImagePath(firstNonBlank(
        props.getProperty("app.skin.bubbleOutgoing"),
        props.getProperty("app.bubbleOutgoingImage")));
    data.setHomeMode(props.getProperty("home.mode"));
    data.setStatusTimeText(props.getProperty("status.time"));
    data.setStatusModeText(props.getProperty("status.mode"));
    data.setStatusSignalText(props.getProperty("status.signal"));
    data.setStatusBatteryText(props.getProperty("status.battery"));

    List<String> contactIds = parseCsv(props.getProperty("contacts"));
    if (contactIds.isEmpty()) {
      contactIds = scanIds(props, CONTACT_KEY_PATTERN, 1);
    }
    for (String contactId : contactIds) {
      if (contactId == null) continue;
      String prefix = "contact." + contactId + ".";
      VnPhoneData.Contact contact = data.getOrCreateContact(contactId);
      contact.setDisplayName(firstNonBlank(
          props.getProperty(prefix + "name"),
          props.getProperty(prefix + "displayName")));
      contact.setAvatarPath(props.getProperty(prefix + "avatar"));
      contact.setColor(props.getProperty(prefix + "color"));
      contact.setSelf(parseBoolean(props.getProperty(prefix + "self"), contact.isSelf()));
    }

    List<String> chatIds = parseCsv(props.getProperty("chats"));
    if (chatIds.isEmpty()) {
      chatIds = scanIds(props, CHAT_KEY_PATTERN, 1);
    }
    data.setChatOrder(chatIds);
    for (String chatId : chatIds) {
      if (chatId == null) continue;
      String prefix = "chat." + chatId + ".";
      VnPhoneData.Chat chat = data.getOrCreateChat(chatId);
      chat.setTitle(props.getProperty(prefix + "title"));
      chat.setIconPath(props.getProperty(prefix + "icon"));
      chat.setParticipants(parseCsv(props.getProperty(prefix + "participants")));
      chat.setUnread(parseBoolean(props.getProperty(prefix + "unread"), false));
      chat.setComposerText(props.getProperty(prefix + "composerText"));
      chat.setComposerHint(props.getProperty(prefix + "composerHint"));

      List<String> messageIds = parseCsv(props.getProperty(prefix + "messages"));
      if (messageIds.isEmpty()) {
        messageIds = scanMessageIds(props, chatId);
      }
      chat.getMessages().clear();
      for (String messageId : messageIds) {
        if (messageId == null) continue;
        String messagePrefix = prefix + "message." + messageId + ".";
        String senderId = props.getProperty(messagePrefix + "sender");
        String text = props.getProperty(messagePrefix + "text");
        String time = props.getProperty(messagePrefix + "time");
        VnPhoneData.MessageType type = VnPhoneData.MessageType.fromToken(props.getProperty(messagePrefix + "type"));
        String asset = props.getProperty(messagePrefix + "asset");
        String caption = props.getProperty(messagePrefix + "caption");
        String duration = props.getProperty(messagePrefix + "duration");
        List<String> options = parsePipeList(props.getProperty(messagePrefix + "options"));
        boolean emptyTextPayload = (senderId == null || senderId.isBlank())
            && (text == null || text.isBlank())
            && (asset == null || asset.isBlank())
            && (caption == null || caption.isBlank())
            && options.isEmpty();
        if (emptyTextPayload) continue;
        chat.getMessages().add(new VnPhoneData.Message(
            messageId,
            senderId,
            text,
            time,
            type,
            asset,
            caption,
            duration,
            options));
      }
    }

    List<String> appIds = parseCsv(props.getProperty("home.apps"));
    if (appIds.isEmpty()) {
      appIds = scanIds(props, PHONE_APP_KEY_PATTERN, 1);
    }
    data.setAppOrder(appIds);
    for (String appId : appIds) {
      if (appId == null) continue;
      String prefix = "phoneapp." + appId + ".";
      VnPhoneData.PhoneApp app = data.getOrCreateApp(appId);
      app.setTitle(props.getProperty(prefix + "title"));
      app.setIconPath(props.getProperty(prefix + "icon"));
      app.setBadgeText(props.getProperty(prefix + "badge"));
      app.setAccentColor(props.getProperty(prefix + "accent"));
      app.setPage(parseInt(props.getProperty(prefix + "page"), 0));
      app.setTargetType(props.getProperty(prefix + "target"));
      app.setTargetValue(props.getProperty(prefix + "targetValue"));
    }

    List<String> callIds = parseCsv(props.getProperty("calls"));
    if (callIds.isEmpty()) {
      callIds = scanIds(props, CALL_KEY_PATTERN, 1);
    }
    data.setCallOrder(callIds);
    for (String callId : callIds) {
      if (callId == null) continue;
      String prefix = "call." + callId + ".";
      VnPhoneData.Call call = data.getOrCreateCall(callId);
      call.setTitle(props.getProperty(prefix + "title"));
      call.setSubtitle(props.getProperty(prefix + "subtitle"));
      call.setAvatarPath(props.getProperty(prefix + "avatar"));
      call.setStatusText(props.getProperty(prefix + "status"));
      call.setParticipantId(props.getProperty(prefix + "participant"));
      call.setVideo(parseBoolean(props.getProperty(prefix + "video"), false));
    }

    for (VnPhoneData.Chat chat : data.getChats().values()) {
      for (String participant : chat.getParticipants()) {
        ensureDisplayContact(data, participant);
      }
      for (VnPhoneData.Message message : chat.getMessages()) {
        if (message.getSenderId() != null) {
          ensureDisplayContact(data, message.getSenderId());
        }
      }
      if (chat.getTitle() == null || chat.getTitle().isBlank()) {
        chat.setTitle(data.defaultChatTitle(chat));
      }
    }
    for (VnPhoneData.Call call : data.getCalls().values()) {
      if (call.getParticipantId() != null) {
        ensureDisplayContact(data, call.getParticipantId());
      }
    }

    return data;
  }

  public static Properties toProperties(VnPhoneData data) {
    Properties props = new Properties();
    if (data == null) return props;

    put(props, "app.title", data.getTitle());
    put(props, "app.subtitle", data.getSubtitle());
    put(props, "app.wallpaper", data.getWallpaperPath());
    put(props, "app.accent", data.getAccentColor());
    put(props, "app.surface", data.getSurfaceColor());
    put(props, "app.bubbleIncoming", data.getIncomingBubbleColor());
    put(props, "app.bubbleOutgoing", data.getOutgoingBubbleColor());
    put(props, "app.skin", data.getSkinId());
    put(props, "app.skin.background", data.getSkinBackgroundPath());
    put(props, "app.skin.topBar", data.getSkinTopBarPath());
    put(props, "app.skin.bottomBar", data.getSkinBottomBarPath());
    put(props, "app.skin.messageField", data.getSkinMessageFieldPath());
    put(props, "app.skin.nav.leading", data.getSkinNavLeadingPath());
    put(props, "app.skin.nav.trailingPrimary", data.getSkinNavTrailingPrimaryPath());
    put(props, "app.skin.nav.trailingSecondary", data.getSkinNavTrailingSecondaryPath());
    put(props, "app.skin.composer.leading", data.getSkinComposerLeadingPath());
    put(props, "app.skin.composer.trailingPrimary", data.getSkinComposerTrailingPrimaryPath());
    put(props, "app.skin.composer.trailingSecondary", data.getSkinComposerTrailingSecondaryPath());
    put(props, "app.skin.statusBackdrop", data.getSkinStatusBackdropPath());
    put(props, "app.skin.statusIcon", data.getSkinStatusIconPath());
    put(props, "app.skin.floatingAction", data.getSkinFloatingActionPath());
    put(props, "app.skin.bubbleIncoming", data.getIncomingBubbleImagePath());
    put(props, "app.skin.bubbleOutgoing", data.getOutgoingBubbleImagePath());
    put(props, "home.mode", data.getHomeMode().token());
    put(props, "status.time", data.getStatusTimeText());
    put(props, "status.mode", data.getStatusModeText());
    put(props, "status.signal", data.getStatusSignalText());
    put(props, "status.battery", data.getStatusBatteryText());

    List<String> contactIds = new ArrayList<>(data.getContacts().keySet());
    props.setProperty("contacts", String.join(",", contactIds));
    for (String contactId : contactIds) {
      VnPhoneData.Contact contact = data.getContacts().get(contactId);
      if (contact == null) continue;
      String prefix = "contact." + contactId + ".";
      put(props, prefix + "name", contact.getDisplayName());
      put(props, prefix + "avatar", contact.getAvatarPath());
      put(props, prefix + "color", contact.getColor());
      props.setProperty(prefix + "self", Boolean.toString(contact.isSelf()));
    }

    List<String> chatIds = new ArrayList<>();
    for (VnPhoneData.Chat chat : data.orderedChats()) {
      if (chat != null && chat.getId() != null && !chat.getId().isBlank()) {
        chatIds.add(chat.getId());
      }
    }
    props.setProperty("chats", String.join(",", chatIds));
    for (String chatId : chatIds) {
      VnPhoneData.Chat chat = data.getChat(chatId);
      if (chat == null) continue;
      String prefix = "chat." + chatId + ".";
      put(props, prefix + "title", firstNonBlank(chat.getTitle(), data.defaultChatTitle(chat)));
      put(props, prefix + "icon", chat.getIconPath());
      props.setProperty(prefix + "participants", String.join(",", chat.getParticipants()));
      props.setProperty(prefix + "unread", Boolean.toString(chat.isUnread()));
      put(props, prefix + "composerText", chat.getComposerText());
      put(props, prefix + "composerHint", chat.getComposerHint());

      List<String> messageIds = new ArrayList<>();
      for (VnPhoneData.Message message : chat.getMessages()) {
        if (message != null && message.getId() != null && !message.getId().isBlank()) {
          messageIds.add(message.getId());
        }
      }
      props.setProperty(prefix + "messages", String.join(",", messageIds));
      for (VnPhoneData.Message message : chat.getMessages()) {
        if (message == null) continue;
        String messagePrefix = prefix + "message." + message.getId() + ".";
        put(props, messagePrefix + "sender", message.getSenderId());
        put(props, messagePrefix + "text", message.getText());
        put(props, messagePrefix + "time", message.getTimeText());
        put(props, messagePrefix + "type", message.getType().token());
        put(props, messagePrefix + "asset", message.getAssetPath());
        put(props, messagePrefix + "caption", message.getCaption());
        put(props, messagePrefix + "duration", message.getDurationText());
        put(props, messagePrefix + "options", joinPipeList(message.getOptions()));
      }
    }

    List<String> appIds = new ArrayList<>();
    for (VnPhoneData.PhoneApp app : data.orderedApps()) {
      if (app != null && app.getId() != null && !app.getId().isBlank()) {
        appIds.add(app.getId());
      }
    }
    props.setProperty("home.apps", String.join(",", appIds));
    for (String appId : appIds) {
      VnPhoneData.PhoneApp app = data.getApp(appId);
      if (app == null) continue;
      String prefix = "phoneapp." + appId + ".";
      put(props, prefix + "title", app.getTitle());
      put(props, prefix + "icon", app.getIconPath());
      put(props, prefix + "badge", app.getBadgeText());
      put(props, prefix + "accent", app.getAccentColor());
      put(props, prefix + "page", Integer.toString(app.getPage()));
      put(props, prefix + "target", app.getTargetType().token());
      put(props, prefix + "targetValue", app.getTargetValue());
    }

    List<String> callIds = new ArrayList<>();
    for (VnPhoneData.Call call : data.orderedCalls()) {
      if (call != null && call.getId() != null && !call.getId().isBlank()) {
        callIds.add(call.getId());
      }
    }
    props.setProperty("calls", String.join(",", callIds));
    for (String callId : callIds) {
      VnPhoneData.Call call = data.getCall(callId);
      if (call == null) continue;
      String prefix = "call." + callId + ".";
      put(props, prefix + "title", call.getTitle());
      put(props, prefix + "subtitle", call.getSubtitle());
      put(props, prefix + "avatar", call.getAvatarPath());
      put(props, prefix + "status", call.getStatusText());
      put(props, prefix + "participant", call.getParticipantId());
      props.setProperty(prefix + "video", Boolean.toString(call.isVideo()));
    }

    return props;
  }

  public static String toPropertiesString(VnPhoneData data) {
    Properties props = toProperties(data);
    try {
      StringWriter out = new StringWriter();
      props.store(out, "JVN Phone Data");
      return out.toString();
    } catch (IOException ex) {
      return "";
    }
  }

  public static byte[] toBytes(VnPhoneData data) {
    return toPropertiesString(data).getBytes(StandardCharsets.UTF_8);
  }

  private static void ensureDisplayContact(VnPhoneData data, String contactId) {
    String normalized = VnPhoneData.normalizeId(contactId);
    if (normalized == null) return;
    VnPhoneData.Contact contact = data.getOrCreateContact(normalized);
    if (contact.getDisplayName() == null || contact.getDisplayName().isBlank()) {
      contact.setDisplayName(contact.getId());
    }
  }

  private static void put(Properties props, String key, String value) {
    if (props == null || key == null || value == null || value.isBlank()) return;
    props.setProperty(key, value);
  }

  private static List<String> parseCsv(String csv) {
    List<String> values = new ArrayList<>();
    if (csv == null || csv.isBlank()) return values;
    Set<String> seen = new LinkedHashSet<>();
    for (String part : csv.split(",")) {
      String normalized = VnPhoneData.normalizeId(part);
      if (normalized != null && seen.add(normalized)) {
        values.add(normalized);
      }
    }
    return values;
  }

  private static List<String> parsePipeList(String raw) {
    List<String> values = new ArrayList<>();
    if (raw == null || raw.isBlank()) return values;
    for (String token : raw.split("\\|")) {
      String normalized = token == null ? null : token.trim();
      if (normalized != null && !normalized.isEmpty()) {
        values.add(normalized);
      }
    }
    return values;
  }

  private static String joinPipeList(List<String> values) {
    if (values == null || values.isEmpty()) return null;
    List<String> normalized = new ArrayList<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) normalized.add(value.trim());
    }
    return normalized.isEmpty() ? null : String.join("|", normalized);
  }

  private static List<String> scanIds(Properties props, Pattern pattern, int groupIndex) {
    Set<String> ids = new LinkedHashSet<>();
    for (String key : props.stringPropertyNames()) {
      Matcher matcher = pattern.matcher(key);
      if (!matcher.matches()) continue;
      String id = matcher.group(groupIndex);
      String normalized = VnPhoneData.normalizeId(id);
      if (normalized != null) ids.add(normalized);
    }
    return new ArrayList<>(ids);
  }

  private static List<String> scanMessageIds(Properties props, String chatId) {
    Set<String> ids = new LinkedHashSet<>();
    String normalizedChatId = VnPhoneData.normalizeId(chatId);
    if (normalizedChatId == null) return new ArrayList<>(ids);
    for (String key : props.stringPropertyNames()) {
      Matcher matcher = MESSAGE_KEY_PATTERN.matcher(key);
      if (!matcher.matches()) continue;
      if (!normalizedChatId.equals(VnPhoneData.normalizeId(matcher.group(1)))) continue;
      String messageId = matcher.group(2);
      if (messageId != null && !messageId.isBlank()) ids.add(messageId.trim());
    }
    return new ArrayList<>(ids);
  }

  private static boolean parseBoolean(String token, boolean fallback) {
    if (token == null || token.isBlank()) return fallback;
    return switch (token.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on" -> true;
      case "false", "0", "no", "off" -> false;
      default -> fallback;
    };
  }

  private static int parseInt(String token, int fallback) {
    if (token == null || token.isBlank()) return fallback;
    try {
      return Integer.parseInt(token.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }
}
