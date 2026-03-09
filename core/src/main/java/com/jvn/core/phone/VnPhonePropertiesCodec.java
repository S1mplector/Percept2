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
        if ((senderId == null || senderId.isBlank()) && (text == null || text.isBlank())) continue;
        chat.getMessages().add(new VnPhoneData.Message(messageId, senderId, text, time));
      }
    }

    // Ensure contacts exist for chat participants and senders even if the config omitted them.
    for (VnPhoneData.Chat chat : data.getChats().values()) {
      for (String participant : chat.getParticipants()) {
        data.getOrCreateContact(participant);
      }
      for (VnPhoneData.Message message : chat.getMessages()) {
        data.getOrCreateContact(message.getSenderId());
      }
      if (chat.getTitle() == null || chat.getTitle().isBlank()) {
        chat.setTitle(data.defaultChatTitle(chat));
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
      }
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

  private static List<String> scanIds(Properties props, Pattern pattern, int group) {
    Set<String> ids = new LinkedHashSet<>();
    if (props == null || props.isEmpty()) return new ArrayList<>();
    for (String key : props.stringPropertyNames()) {
      Matcher matcher = pattern.matcher(key);
      if (!matcher.matches()) continue;
      String id = matcher.group(group);
      String normalized = VnPhoneData.normalizeId(id);
      if (normalized != null) ids.add(normalized);
    }
    return new ArrayList<>(ids);
  }

  private static List<String> scanMessageIds(Properties props, String chatId) {
    Set<String> ids = new LinkedHashSet<>();
    String normalizedChatId = VnPhoneData.normalizeId(chatId);
    if (props == null || normalizedChatId == null) return new ArrayList<>();
    for (String key : props.stringPropertyNames()) {
      Matcher matcher = MESSAGE_KEY_PATTERN.matcher(key);
      if (!matcher.matches()) continue;
      if (!normalizedChatId.equals(VnPhoneData.normalizeId(matcher.group(1)))) continue;
      String messageId = matcher.group(2);
      if (messageId != null && !messageId.isBlank()) ids.add(messageId.trim());
    }
    return new ArrayList<>(ids);
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on" -> true;
      case "false", "0", "no", "off" -> false;
      default -> fallback;
    };
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }
}
