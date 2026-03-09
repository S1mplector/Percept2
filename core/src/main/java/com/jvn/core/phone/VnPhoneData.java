package com.jvn.core.phone;

import java.util.ArrayList;
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
 * <p>The model is deliberately simple and property-file-friendly so it can be
 * persisted through the existing VN variable/save/rollback path as a single
 * serialized string.</p>
 */
public final class VnPhoneData {
  private static final String DEFAULT_TITLE = "Phone";
  private static final String DEFAULT_SUBTITLE = "Messages";

  private String title = DEFAULT_TITLE;
  private String subtitle = DEFAULT_SUBTITLE;
  private String wallpaperPath;
  private String accentColor = "#78b7ff";
  private String surfaceColor = "#101826";
  private String incomingBubbleColor = "#1c2738";
  private String outgoingBubbleColor = "#2563eb";

  private final LinkedHashMap<String, Contact> contacts = new LinkedHashMap<>();
  private final LinkedHashMap<String, Chat> chats = new LinkedHashMap<>();
  private final List<String> chatOrder = new ArrayList<>();

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

  public Map<String, Contact> getContacts() {
    return contacts;
  }

  public Map<String, Chat> getChats() {
    return chats;
  }

  public List<String> getChatOrder() {
    return chatOrder;
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

  public void removeChat(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) return;
    chats.remove(normalized);
    chatOrder.removeIf(normalized::equals);
  }

  public void clearChats() {
    chats.clear();
    chatOrder.clear();
  }

  public void setChatOrder(List<String> order) {
    chatOrder.clear();
    if (order == null) return;
    Set<String> seen = new LinkedHashSet<>();
    for (String id : order) {
      String normalized = normalizeId(id);
      if (normalized != null && seen.add(normalized)) {
        chatOrder.add(normalized);
      }
    }
  }

  public List<Chat> orderedChats() {
    List<Chat> ordered = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String id : chatOrder) {
      Chat chat = chats.get(id);
      if (chat != null && seen.add(id)) ordered.add(chat);
    }
    for (Map.Entry<String, Chat> entry : chats.entrySet()) {
      if (seen.add(entry.getKey())) ordered.add(entry.getValue());
    }
    return ordered;
  }

  public void moveChatToFront(String id) {
    String normalized = normalizeId(id);
    if (normalized == null) return;
    ensureChatOrder(normalized);
    chatOrder.removeIf(normalized::equals);
    chatOrder.add(0, normalized);
  }

  public void appendMessage(String chatId, String senderId, String text, String timeText, boolean unread) {
    Chat chat = getOrCreateChat(chatId);
    Contact sender = getOrCreateContact(senderId);
    if (sender.getDisplayName() == null || sender.getDisplayName().isBlank()) {
      sender.setDisplayName(sender.getId());
    }
    chat.addParticipant(sender.getId());
    if (chat.getTitle() == null || chat.getTitle().isBlank()) {
      chat.setTitle(defaultChatTitle(chat));
    }
    chat.getMessages().add(new Message(nextMessageId(chat), sender.getId(), text, blankToNull(timeText)));
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
    String normalized = normalizeId(chatId);
    if (normalized == null) return;
    if (!chatOrder.contains(normalized)) {
      chatOrder.add(normalized);
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

    public Message getLastMessage() {
      return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public String getLastPreview() {
      Message last = getLastMessage();
      if (last == null || last.getText() == null || last.getText().isBlank()) return "No messages yet";
      return last.getText();
    }
  }

  public static final class Message {
    private final String id;
    private final String senderId;
    private final String text;
    private final String timeText;

    public Message(String id, String senderId, String text, String timeText) {
      String normalizedId = blankToNull(id);
      this.id = normalizedId == null ? "message" : normalizedId;
      String normalizedSender = normalizeId(senderId);
      this.senderId = normalizedSender == null ? "unknown" : normalizedSender;
      this.text = Objects.requireNonNullElse(text, "");
      this.timeText = blankToNull(timeText);
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
  }
}
