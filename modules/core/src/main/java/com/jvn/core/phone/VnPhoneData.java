package com.jvn.core.phone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable phone data model used by runtime, preview, and project config loading.
 *
 * <p>The model stays property-file-friendly so it can be serialized through the
 * existing VN variable/save path as a single string while still supporting a
 * fuller "phone system" surface.</p>
 */
public final class VnPhoneData {
  private static final String DEFAULT_TITLE = "Phone";
  private static final String DEFAULT_SUBTITLE = "Messages";
  private static final String DEFAULT_STATUS_TIME = "9:41";
  private static final String DEFAULT_STATUS_SIGNAL = "LTE";
  private static final String DEFAULT_STATUS_BATTERY = "100%";

  private String title = DEFAULT_TITLE;
  private String subtitle = DEFAULT_SUBTITLE;
  private String wallpaperPath;
  private String accentColor = "#78b7ff";
  private String surfaceColor = "#101826";
  private String incomingBubbleColor = "#1c2738";
  private String outgoingBubbleColor = "#2563eb";
  private String skinId = "default";
  private String skinBackgroundPath;
  private String skinTopBarPath;
  private String skinBottomBarPath;
  private String skinMessageFieldPath;
  private String skinNavLeadingPath;
  private String skinNavTrailingPrimaryPath;
  private String skinNavTrailingSecondaryPath;
  private String skinComposerLeadingPath;
  private String skinComposerTrailingPrimaryPath;
  private String skinComposerTrailingSecondaryPath;
  private String skinStatusBackdropPath;
  private String skinStatusIconPath;
  private String skinFloatingActionPath;
  private String incomingBubbleImagePath;
  private String outgoingBubbleImagePath;
  private HomeMode homeMode = HomeMode.THREADS;
  private String statusTimeText = DEFAULT_STATUS_TIME;
  private String statusModeText;
  private String statusSignalText = DEFAULT_STATUS_SIGNAL;
  private String statusBatteryText = DEFAULT_STATUS_BATTERY;

  private final LinkedHashMap<String, Contact> contacts = new LinkedHashMap<>();
  private final LinkedHashMap<String, Chat> chats = new LinkedHashMap<>();
  private final List<String> chatOrder = new ArrayList<>();
  private final LinkedHashMap<String, PhoneApp> apps = new LinkedHashMap<>();
  private final List<String> appOrder = new ArrayList<>();
  private final LinkedHashMap<String, Call> calls = new LinkedHashMap<>();
  private final List<String> callOrder = new ArrayList<>();

  public String getTitle() {
    return title == null || title.isBlank() ? DEFAULT_TITLE : title;
  }

  public void setTitle(String title) {
    this.title = blankToNull(title);
  }

  public String getSubtitle() {
    return subtitle == null || subtitle.isBlank() ? DEFAULT_SUBTITLE : subtitle;
  }

  public void setSubtitle(String subtitle) {
    this.subtitle = blankToNull(subtitle);
  }

  public String getWallpaperPath() {
    return wallpaperPath;
  }

  public void setWallpaperPath(String wallpaperPath) {
    this.wallpaperPath = blankToNull(wallpaperPath);
  }

  public String getAccentColor() {
    return accentColor;
  }

  public void setAccentColor(String accentColor) {
    this.accentColor = blankToNull(accentColor);
  }

  public String getSurfaceColor() {
    return surfaceColor;
  }

  public void setSurfaceColor(String surfaceColor) {
    this.surfaceColor = blankToNull(surfaceColor);
  }

  public String getIncomingBubbleColor() {
    return incomingBubbleColor;
  }

  public void setIncomingBubbleColor(String incomingBubbleColor) {
    this.incomingBubbleColor = blankToNull(incomingBubbleColor);
  }

  public String getOutgoingBubbleColor() {
    return outgoingBubbleColor;
  }

  public void setOutgoingBubbleColor(String outgoingBubbleColor) {
    this.outgoingBubbleColor = blankToNull(outgoingBubbleColor);
  }

  public String getSkinId() {
    return skinId == null || skinId.isBlank() ? "default" : skinId;
  }

  public void setSkinId(String skinId) {
    String normalized = blankToNull(skinId);
    this.skinId = normalized == null ? "default" : normalized.toLowerCase(Locale.ROOT);
  }

  public String getSkinBackgroundPath() {
    return skinBackgroundPath;
  }

  public void setSkinBackgroundPath(String skinBackgroundPath) {
    this.skinBackgroundPath = blankToNull(skinBackgroundPath);
  }

  public String getSkinTopBarPath() {
    return skinTopBarPath;
  }

  public void setSkinTopBarPath(String skinTopBarPath) {
    this.skinTopBarPath = blankToNull(skinTopBarPath);
  }

  public String getSkinBottomBarPath() {
    return skinBottomBarPath;
  }

  public void setSkinBottomBarPath(String skinBottomBarPath) {
    this.skinBottomBarPath = blankToNull(skinBottomBarPath);
  }

  public String getSkinMessageFieldPath() {
    return skinMessageFieldPath;
  }

  public void setSkinMessageFieldPath(String skinMessageFieldPath) {
    this.skinMessageFieldPath = blankToNull(skinMessageFieldPath);
  }

  public String getSkinNavLeadingPath() {
    return skinNavLeadingPath;
  }

  public void setSkinNavLeadingPath(String skinNavLeadingPath) {
    this.skinNavLeadingPath = blankToNull(skinNavLeadingPath);
  }

  public String getSkinNavTrailingPrimaryPath() {
    return skinNavTrailingPrimaryPath;
  }

  public void setSkinNavTrailingPrimaryPath(String skinNavTrailingPrimaryPath) {
    this.skinNavTrailingPrimaryPath = blankToNull(skinNavTrailingPrimaryPath);
  }

  public String getSkinNavTrailingSecondaryPath() {
    return skinNavTrailingSecondaryPath;
  }

  public void setSkinNavTrailingSecondaryPath(String skinNavTrailingSecondaryPath) {
    this.skinNavTrailingSecondaryPath = blankToNull(skinNavTrailingSecondaryPath);
  }

  public String getSkinComposerLeadingPath() {
    return skinComposerLeadingPath;
  }

  public void setSkinComposerLeadingPath(String skinComposerLeadingPath) {
    this.skinComposerLeadingPath = blankToNull(skinComposerLeadingPath);
  }

  public String getSkinComposerTrailingPrimaryPath() {
    return skinComposerTrailingPrimaryPath;
  }

  public void setSkinComposerTrailingPrimaryPath(String skinComposerTrailingPrimaryPath) {
    this.skinComposerTrailingPrimaryPath = blankToNull(skinComposerTrailingPrimaryPath);
  }

  public String getSkinComposerTrailingSecondaryPath() {
    return skinComposerTrailingSecondaryPath;
  }

  public void setSkinComposerTrailingSecondaryPath(String skinComposerTrailingSecondaryPath) {
    this.skinComposerTrailingSecondaryPath = blankToNull(skinComposerTrailingSecondaryPath);
  }

  public String getSkinStatusBackdropPath() {
    return skinStatusBackdropPath;
  }

  public void setSkinStatusBackdropPath(String skinStatusBackdropPath) {
    this.skinStatusBackdropPath = blankToNull(skinStatusBackdropPath);
  }

  public String getSkinStatusIconPath() {
    return skinStatusIconPath;
  }

  public void setSkinStatusIconPath(String skinStatusIconPath) {
    this.skinStatusIconPath = blankToNull(skinStatusIconPath);
  }

  public String getSkinFloatingActionPath() {
    return skinFloatingActionPath;
  }

  public void setSkinFloatingActionPath(String skinFloatingActionPath) {
    this.skinFloatingActionPath = blankToNull(skinFloatingActionPath);
  }

  public String getIncomingBubbleImagePath() {
    return incomingBubbleImagePath;
  }

  public void setIncomingBubbleImagePath(String incomingBubbleImagePath) {
    this.incomingBubbleImagePath = blankToNull(incomingBubbleImagePath);
  }

  public String getOutgoingBubbleImagePath() {
    return outgoingBubbleImagePath;
  }

  public void setOutgoingBubbleImagePath(String outgoingBubbleImagePath) {
    this.outgoingBubbleImagePath = blankToNull(outgoingBubbleImagePath);
  }

  public HomeMode getHomeMode() {
    return homeMode == null ? HomeMode.THREADS : homeMode;
  }

  public void setHomeMode(HomeMode homeMode) {
    this.homeMode = homeMode == null ? HomeMode.THREADS : homeMode;
  }

  public void setHomeMode(String token) {
    this.homeMode = HomeMode.fromToken(token);
  }

  public String getStatusTimeText() {
    return firstNonBlank(statusTimeText, DEFAULT_STATUS_TIME);
  }

  public void setStatusTimeText(String statusTimeText) {
    this.statusTimeText = blankToNull(statusTimeText);
  }

  public String getStatusModeText() {
    return statusModeText;
  }

  public void setStatusModeText(String statusModeText) {
    this.statusModeText = blankToNull(statusModeText);
  }

  public String getStatusSignalText() {
    return firstNonBlank(statusSignalText, DEFAULT_STATUS_SIGNAL);
  }

  public void setStatusSignalText(String statusSignalText) {
    this.statusSignalText = blankToNull(statusSignalText);
  }

  public String getStatusBatteryText() {
    return firstNonBlank(statusBatteryText, DEFAULT_STATUS_BATTERY);
  }

  public void setStatusBatteryText(String statusBatteryText) {
    this.statusBatteryText = blankToNull(statusBatteryText);
  }

  public Map<String, Contact> getContacts() {
    return contacts;
  }

  public Map<String, Chat> getChats() {
    return chats;
  }

  public List<String> getChatOrder() {
    return chatOrder;
  }

  public Map<String, PhoneApp> getApps() {
    return apps;
  }

  public List<String> getAppOrder() {
    return appOrder;
  }

  public Map<String, Call> getCalls() {
    return calls;
  }

  public List<String> getCallOrder() {
    return callOrder;
  }

  public Contact getContact(String id) {
    return contacts.get(normalizeId(id));
  }

  public Contact getOrCreateContact(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) throw new IllegalArgumentException("contact id cannot be blank");
    return contacts.computeIfAbsent(normalized, Contact::new);
  }

  public Chat getChat(String id) {
    return chats.get(normalizeId(id));
  }

  public Chat getOrCreateChat(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) throw new IllegalArgumentException("chat id cannot be blank");
    Chat chat = chats.computeIfAbsent(normalized, Chat::new);
    ensureChatOrder(normalized);
    return chat;
  }

  public PhoneApp getApp(String id) {
    return apps.get(normalizeId(id));
  }

  public PhoneApp getOrCreateApp(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) throw new IllegalArgumentException("app id cannot be blank");
    PhoneApp app = apps.computeIfAbsent(normalized, PhoneApp::new);
    ensureAppOrder(normalized);
    return app;
  }

  public Call getCall(String id) {
    return calls.get(normalizeId(id));
  }

  public Call getOrCreateCall(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) throw new IllegalArgumentException("call id cannot be blank");
    Call call = calls.computeIfAbsent(normalized, Call::new);
    ensureCallOrder(normalized);
    return call;
  }

  public void removeChat(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) return;
    chats.remove(normalized);
    chatOrder.removeIf(normalized::equals);
  }

  public void removeApp(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) return;
    apps.remove(normalized);
    appOrder.removeIf(normalized::equals);
  }

  public void removeCall(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) return;
    calls.remove(normalized);
    callOrder.removeIf(normalized::equals);
  }

  public void clearChats() {
    chats.clear();
    chatOrder.clear();
  }

  public void clearApps() {
    apps.clear();
    appOrder.clear();
  }

  public void clearCalls() {
    calls.clear();
    callOrder.clear();
  }

  public void setChatOrder(List<String> order) {
    replaceOrder(chatOrder, order);
  }

  public void setAppOrder(List<String> order) {
    replaceOrder(appOrder, order);
  }

  public void setCallOrder(List<String> order) {
    replaceOrder(callOrder, order);
  }

  public List<Chat> orderedChats() {
    return orderedValues(chats, chatOrder);
  }

  public List<PhoneApp> orderedApps() {
    return orderedValues(apps, appOrder);
  }

  public List<Call> orderedCalls() {
    return orderedValues(calls, callOrder);
  }

  public void moveChatToFront(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) return;
    ensureChatOrder(normalized);
    chatOrder.removeIf(normalized::equals);
    chatOrder.add(0, normalized);
  }

  public void appendMessage(String chatId, String senderId, String text, String timeText, boolean unread) {
    appendMessage(chatId, senderId, text, timeText, MessageType.TEXT, null, null, null, List.of(), unread);
  }

  public void appendMessage(String chatId,
                            String senderId,
                            String text,
                            String timeText,
                            MessageType type,
                            String assetPath,
                            String caption,
                            String durationText,
                            List<String> options,
                            boolean unread) {
    Chat chat = getOrCreateChat(chatId);
    String normalizedSender = normalizeId(senderId);
    if (normalizedSender != null) {
      Contact sender = getOrCreateContact(normalizedSender);
      if (sender.getDisplayName() == null || sender.getDisplayName().isBlank()) {
        sender.setDisplayName(sender.getId());
      }
      chat.addParticipant(sender.getId());
    }
    if (chat.getTitle() == null || chat.getTitle().isBlank()) {
      chat.setTitle(defaultChatTitle(chat));
    }
    chat.getMessages().add(new Message(
        nextMessageId(chat),
        normalizedSender,
        text,
        blankToNull(timeText),
        type,
        assetPath,
        caption,
        durationText,
        options));
    chat.setUnread(unread);
    moveChatToFront(chat.getId());
  }

  public void clearMessages(String chatId) {
    Chat chat = getChat(chatId);
    if (chat == null) return;
    chat.getMessages().clear();
    chat.setUnread(false);
  }

  public void markChatUnread(String chatId, boolean unread) {
    Chat chat = getChat(chatId);
    if (chat == null) return;
    chat.setUnread(unread);
  }

  public String defaultChatTitle(Chat chat) {
    if (chat == null) return "Conversation";
    if (chat.getTitle() != null && !chat.getTitle().isBlank()) return chat.getTitle();
    List<String> participants = chat.getParticipants();
    if (participants.size() == 1) {
      Contact contact = getContact(participants.get(0));
      if (contact != null && contact.getDisplayName() != null && !contact.getDisplayName().isBlank()) {
        return contact.getDisplayName();
      }
      return participants.get(0);
    }
    if (participants.isEmpty()) return "Conversation";
    List<String> names = new ArrayList<>();
    for (String participant : participants) {
      Contact contact = getContact(participant);
      names.add(contact != null && contact.getDisplayName() != null && !contact.getDisplayName().isBlank()
          ? contact.getDisplayName()
          : participant);
    }
    return String.join(", ", names);
  }

  public void ensureChatOrder(String chatId) {
    ensureOrder(chatOrder, chatId);
  }

  public void ensureAppOrder(String appId) {
    ensureOrder(appOrder, appId);
  }

  public void ensureCallOrder(String callId) {
    ensureOrder(callOrder, callId);
  }

  private static void ensureOrder(List<String> order, String id) {
    String normalized = normalizeId(id);
    if (normalized == null || order == null) return;
    if (!order.contains(normalized)) {
      order.add(normalized);
    }
  }

  private static <T> List<T> orderedValues(LinkedHashMap<String, T> map, List<String> order) {
    List<T> ordered = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String id : order) {
      T item = map.get(id);
      if (item != null && seen.add(id)) ordered.add(item);
    }
    for (Map.Entry<String, T> entry : map.entrySet()) {
      if (seen.add(entry.getKey())) ordered.add(entry.getValue());
    }
    return ordered;
  }

  private static void replaceOrder(List<String> target, List<String> source) {
    target.clear();
    if (source == null) return;
    Set<String> seen = new LinkedHashSet<>();
    for (String id : source) {
      String normalized = normalizeId(id);
      if (normalized != null && seen.add(normalized)) {
        target.add(normalized);
      }
    }
  }

  private static String nextMessageId(Chat chat) {
    int idx = chat == null ? 1 : chat.getMessages().size() + 1;
    String candidate = "m" + idx;
    if (chat == null) return candidate;
    Set<String> used = new LinkedHashSet<>();
    for (Message message : chat.getMessages()) {
      if (message != null && message.getId() != null && !message.getId().isBlank()) {
        used.add(message.getId());
      }
    }
    while (used.contains(candidate)) {
      idx++;
      candidate = "m" + idx;
    }
    return candidate;
  }

  public static String normalizeId(String raw) {
    if (raw == null) return null;
    String value = raw.trim();
    if (value.isEmpty()) return null;
    return value.toLowerCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return "";
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  public enum HomeMode {
    THREADS("threads"),
    APPS("apps");

    private final String token;

    HomeMode(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    public static HomeMode fromToken(String token) {
      if (token == null || token.isBlank()) return THREADS;
      return "apps".equalsIgnoreCase(token.trim()) ? APPS : THREADS;
    }
  }

  public enum AppTargetType {
    NONE("none"),
    HOME("home"),
    CHAT("chat"),
    CALL("call"),
    LABEL("label");

    private final String token;

    AppTargetType(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    public static AppTargetType fromToken(String token) {
      if (token == null || token.isBlank()) return NONE;
      String normalized = token.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
        case "home" -> HOME;
        case "chat", "thread" -> CHAT;
        case "call", "video", "voice" -> CALL;
        case "label" -> LABEL;
        default -> NONE;
      };
    }
  }

  public enum MessageType {
    TEXT("text"),
    IMAGE("image"),
    AUDIO("audio"),
    MENU("menu"),
    DATE("date"),
    LABEL("label");

    private final String token;

    MessageType(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    public boolean isSystemType() {
      return this == DATE || this == LABEL;
    }

    public static MessageType fromToken(String token) {
      if (token == null || token.isBlank()) return TEXT;
      String normalized = token.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
        case "image", "photo" -> IMAGE;
        case "audio", "voice" -> AUDIO;
        case "menu", "choices", "choice" -> MENU;
        case "date" -> DATE;
        case "label", "marker" -> LABEL;
        default -> TEXT;
      };
    }
  }

  public static final class Contact {
    private final String id;
    private String displayName;
    private String avatarPath;
    private String color;
    private boolean self;

    public Contact(String id) {
      String normalized = normalizeId(id);
      if (normalized == null) throw new IllegalArgumentException("contact id cannot be blank");
      this.id = normalized;
    }

    public String getId() {
      return id;
    }

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = blankToNull(displayName);
    }

    public String getAvatarPath() {
      return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
      this.avatarPath = blankToNull(avatarPath);
    }

    public String getColor() {
      return color;
    }

    public void setColor(String color) {
      this.color = blankToNull(color);
    }

    public boolean isSelf() {
      return self || "mc".equals(id) || "player".equals(id) || "self".equals(id);
    }

    public void setSelf(boolean self) {
      this.self = self;
    }
  }

  public static final class Chat {
    private final String id;
    private String title;
    private String iconPath;
    private final List<String> participants = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();
    private boolean unread;
    private String composerText;
    private String composerHint;

    public Chat(String id) {
      String normalized = normalizeId(id);
      if (normalized == null) throw new IllegalArgumentException("chat id cannot be blank");
      this.id = normalized;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = blankToNull(title);
    }

    public String getIconPath() {
      return iconPath;
    }

    public void setIconPath(String iconPath) {
      this.iconPath = blankToNull(iconPath);
    }

    public List<String> getParticipants() {
      return participants;
    }

    public void setParticipants(List<String> participants) {
      this.participants.clear();
      if (participants == null) return;
      for (String participant : participants) {
        addParticipant(participant);
      }
    }

    public void addParticipant(String participantId) {
      String normalized = normalizeId(participantId);
      if (normalized == null || participants.contains(normalized)) return;
      participants.add(normalized);
    }

    public List<Message> getMessages() {
      return messages;
    }

    public boolean isUnread() {
      return unread;
    }

    public void setUnread(boolean unread) {
      this.unread = unread;
    }

    public String getComposerText() {
      return composerText;
    }

    public void setComposerText(String composerText) {
      this.composerText = blankToNull(composerText);
    }

    public String getComposerHint() {
      return composerHint;
    }

    public void setComposerHint(String composerHint) {
      this.composerHint = blankToNull(composerHint);
    }

    public Message getLastMessage() {
      return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public String getLastPreview() {
      Message last = getLastMessage();
      return last == null ? "No messages yet" : last.getPreviewText();
    }
  }

  public static final class PhoneApp {
    private final String id;
    private String title;
    private String iconPath;
    private String badgeText;
    private String accentColor;
    private int page;
    private AppTargetType targetType = AppTargetType.NONE;
    private String targetValue;

    public PhoneApp(String id) {
      String normalized = normalizeId(id);
      if (normalized == null) throw new IllegalArgumentException("app id cannot be blank");
      this.id = normalized;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = blankToNull(title);
    }

    public String getIconPath() {
      return iconPath;
    }

    public void setIconPath(String iconPath) {
      this.iconPath = blankToNull(iconPath);
    }

    public String getBadgeText() {
      return badgeText;
    }

    public void setBadgeText(String badgeText) {
      this.badgeText = blankToNull(badgeText);
    }

    public String getAccentColor() {
      return accentColor;
    }

    public void setAccentColor(String accentColor) {
      this.accentColor = blankToNull(accentColor);
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = Math.max(0, page);
    }

    public AppTargetType getTargetType() {
      return targetType == null ? AppTargetType.NONE : targetType;
    }

    public void setTargetType(AppTargetType targetType) {
      this.targetType = targetType == null ? AppTargetType.NONE : targetType;
    }

    public void setTargetType(String token) {
      this.targetType = AppTargetType.fromToken(token);
    }

    public String getTargetValue() {
      return targetValue;
    }

    public void setTargetValue(String targetValue) {
      this.targetValue = blankToNull(targetValue);
    }
  }

  public static final class Call {
    private final String id;
    private String title;
    private String subtitle;
    private String avatarPath;
    private String statusText;
    private String participantId;
    private boolean video;

    public Call(String id) {
      String normalized = normalizeId(id);
      if (normalized == null) throw new IllegalArgumentException("call id cannot be blank");
      this.id = normalized;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = blankToNull(title);
    }

    public String getSubtitle() {
      return subtitle;
    }

    public void setSubtitle(String subtitle) {
      this.subtitle = blankToNull(subtitle);
    }

    public String getAvatarPath() {
      return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
      this.avatarPath = blankToNull(avatarPath);
    }

    public String getStatusText() {
      return statusText;
    }

    public void setStatusText(String statusText) {
      this.statusText = blankToNull(statusText);
    }

    public String getParticipantId() {
      return participantId;
    }

    public void setParticipantId(String participantId) {
      this.participantId = normalizeId(participantId);
    }

    public boolean isVideo() {
      return video;
    }

    public void setVideo(boolean video) {
      this.video = video;
    }
  }

  public static final class Message {
    private final String id;
    private final String senderId;
    private final String text;
    private final String timeText;
    private final MessageType type;
    private final String assetPath;
    private final String caption;
    private final String durationText;
    private final List<String> options;

    public Message(String id, String senderId, String text, String timeText) {
      this(id, senderId, text, timeText, MessageType.TEXT, null, null, null, List.of());
    }

    public Message(String id,
                   String senderId,
                   String text,
                   String timeText,
                   MessageType type,
                   String assetPath,
                   String caption,
                   String durationText,
                   List<String> options) {
      String normalizedId = blankToNull(id);
      this.id = normalizedId == null ? "message" : normalizedId;
      this.senderId = normalizeId(senderId);
      this.text = Objects.requireNonNullElse(text, "");
      this.timeText = blankToNull(timeText);
      this.type = type == null ? MessageType.TEXT : type;
      this.assetPath = blankToNull(assetPath);
      this.caption = blankToNull(caption);
      this.durationText = blankToNull(durationText);
      List<String> normalizedOptions = new ArrayList<>();
      if (options != null) {
        for (String option : options) {
          String normalized = blankToNull(option);
          if (normalized != null) normalizedOptions.add(normalized);
        }
      }
      this.options = Collections.unmodifiableList(normalizedOptions);
    }

    public String getId() {
      return id;
    }

    public String getSenderId() {
      return senderId;
    }

    public String getText() {
      return text;
    }

    public String getTimeText() {
      return timeText;
    }

    public MessageType getType() {
      return type;
    }

    public String getAssetPath() {
      return assetPath;
    }

    public String getCaption() {
      return caption;
    }

    public String getDurationText() {
      return durationText;
    }

    public List<String> getOptions() {
      return options;
    }

    public String getPreviewText() {
      return switch (type) {
        case IMAGE -> joinPreview("[Image]", firstNonBlank(caption, text, assetPath));
        case AUDIO -> joinPreview("[Audio]", firstNonBlank(caption, durationText, text, assetPath));
        case MENU -> options.isEmpty() ? "[Menu]" : "[Menu] " + options.get(0);
        case DATE -> firstNonBlank(text, caption, "Date");
        case LABEL -> firstNonBlank(text, caption, "Marker");
        case TEXT -> firstNonBlank(text, caption, "Message");
      };
    }

    private static String joinPreview(String prefix, String value) {
      String resolved = blankToNull(value);
      return resolved == null ? prefix : prefix + " " + resolved;
    }
  }
}
