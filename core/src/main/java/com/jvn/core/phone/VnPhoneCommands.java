package com.jvn.core.phone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnState;

/**
 * Shared phone command handler used by runtime and preview.
 */
public final class VnPhoneCommands {
  private VnPhoneCommands() {
  }

  public enum Action {
    NONE,
    OPEN_HOME,
    OPEN_CHAT,
    CLOSE
  }

  public record Result(Action action, String chatId) {
    public static Result none() {
      return new Result(Action.NONE, null);
    }

    public static Result openHome() {
      return new Result(Action.OPEN_HOME, null);
    }

    public static Result openChat(String chatId) {
      return new Result(Action.OPEN_CHAT, VnPhoneData.normalizeId(chatId));
    }

    public static Result close() {
      return new Result(Action.CLOSE, null);
    }
  }

  public static Result handle(String payload, VnScene scene, Supplier<VnPhoneData> seedSupplier) {
    if (scene == null || scene.getState() == null) return Result.none();
    VnState state = scene.getState();
    List<String> tokens = VnArgTokenizer.tokenize(payload);
    if (tokens.isEmpty()) {
      state.showHudMessage("phone: missing command", 1200);
      return Result.none();
    }

    String cmd = tokens.get(0).trim().toLowerCase(Locale.ROOT);
    return switch (cmd) {
      case "open", "show" -> handleOpen(tokens, state);
      case "chat" -> handleOpenChat(tokens, state);
      case "close", "hide" -> Result.close();
      case "contact" -> mutateContacts(tokens, state, seedSupplier);
      case "thread" -> mutateThreads(tokens, state, seedSupplier);
      case "message" -> mutateMessages(tokens, state, seedSupplier);
      case "unread" -> mutateUnread(tokens, state, seedSupplier);
      case "clear" -> mutateClear(tokens, state, seedSupplier);
      default -> {
        state.showHudMessage("phone: unknown command '" + cmd + "'", 1400);
        yield Result.none();
      }
    };
  }

  private static Result handleOpen(List<String> tokens, VnState state) {
    if (tokens.size() <= 1) return Result.openHome();
    String second = tokens.get(1).trim().toLowerCase(Locale.ROOT);
    if ("home".equals(second)) return Result.openHome();
    if ("chat".equals(second)) {
      if (tokens.size() < 3) {
        state.showHudMessage("phone open chat: missing chat id", 1400);
        return Result.none();
      }
      return Result.openChat(tokens.get(2));
    }
    return Result.openChat(tokens.get(1));
  }

  private static Result handleOpenChat(List<String> tokens, VnState state) {
    if (tokens.size() < 2) {
      state.showHudMessage("phone chat: missing chat id", 1400);
      return Result.none();
    }
    return Result.openChat(tokens.get(1));
  }

  private static Result mutateContacts(List<String> tokens, VnState state, Supplier<VnPhoneData> seedSupplier) {
    if (tokens.size() < 2) {
      state.showHudMessage("phone contact: missing id", 1400);
      return Result.none();
    }
    VnPhoneData data = VnPhoneStateStore.load(state, seedSupplier);
    VnPhoneData.Contact contact = data.getOrCreateContact(tokens.get(1));
    for (KeyValue option : parseOptions(tokens, 2)) {
      switch (option.key()) {
        case "name", "display", "displayname" -> contact.setDisplayName(option.value());
        case "avatar", "icon" -> contact.setAvatarPath(option.value());
        case "color", "accent" -> contact.setColor(option.value());
        case "self", "player" -> contact.setSelf(parseBoolean(option.value(), contact.isSelf()));
        default -> state.showHudMessage("phone contact: ignored option '" + option.key() + "'", 900);
      }
    }
    if (contact.getDisplayName() == null || contact.getDisplayName().isBlank()) {
      contact.setDisplayName(contact.getId());
    }
    VnPhoneStateStore.save(state, data);
    return Result.none();
  }

  private static Result mutateThreads(List<String> tokens, VnState state, Supplier<VnPhoneData> seedSupplier) {
    if (tokens.size() < 2) {
      state.showHudMessage("phone thread: missing id", 1400);
      return Result.none();
    }
    VnPhoneData data = VnPhoneStateStore.load(state, seedSupplier);
    VnPhoneData.Chat chat = data.getOrCreateChat(tokens.get(1));
    for (KeyValue option : parseOptions(tokens, 2)) {
      switch (option.key()) {
        case "title", "name" -> chat.setTitle(option.value());
        case "icon", "avatar" -> chat.setIconPath(option.value());
        case "participants", "members" -> {
          chat.setParticipants(splitCsv(option.value()));
          for (String participant : chat.getParticipants()) {
            VnPhoneData.Contact contact = data.getOrCreateContact(participant);
            if (contact.getDisplayName() == null || contact.getDisplayName().isBlank()) {
              contact.setDisplayName(contact.getId());
            }
          }
        }
        case "unread" -> chat.setUnread(parseBoolean(option.value(), chat.isUnread()));
        default -> state.showHudMessage("phone thread: ignored option '" + option.key() + "'", 900);
      }
    }
    if (chat.getTitle() == null || chat.getTitle().isBlank()) {
      chat.setTitle(data.defaultChatTitle(chat));
    }
    VnPhoneStateStore.save(state, data);
    return Result.none();
  }

  private static Result mutateMessages(List<String> tokens, VnState state, Supplier<VnPhoneData> seedSupplier) {
    if (tokens.size() < 4) {
      state.showHudMessage("phone message: expected chat sender text", 1500);
      return Result.none();
    }
    String chatId = tokens.get(1);
    String senderId = tokens.get(2);
    List<KeyValue> options = new ArrayList<>();
    String text = collectMessageText(tokens, 3, options);
    if (text == null || text.isBlank()) {
      state.showHudMessage("phone message: missing text", 1400);
      return Result.none();
    }

    String time = null;
    boolean unread = true;
    for (KeyValue option : options) {
      switch (option.key()) {
        case "time", "timestamp", "at" -> time = option.value();
        case "unread" -> unread = parseBoolean(option.value(), true);
        default -> state.showHudMessage("phone message: ignored option '" + option.key() + "'", 900);
      }
    }

    VnPhoneData data = VnPhoneStateStore.load(state, seedSupplier);
    data.appendMessage(chatId, senderId, text, time, unread);
    VnPhoneStateStore.save(state, data);
    return Result.none();
  }

  private static Result mutateUnread(List<String> tokens, VnState state, Supplier<VnPhoneData> seedSupplier) {
    if (tokens.size() < 2) {
      state.showHudMessage("phone unread: missing chat id", 1400);
      return Result.none();
    }
    boolean unread = tokens.size() < 3 || parseBoolean(tokens.get(2), true);
    VnPhoneData data = VnPhoneStateStore.load(state, seedSupplier);
    data.markChatUnread(tokens.get(1), unread);
    VnPhoneStateStore.save(state, data);
    return Result.none();
  }

  private static Result mutateClear(List<String> tokens, VnState state, Supplier<VnPhoneData> seedSupplier) {
    if (tokens.size() < 2) {
      state.showHudMessage("phone clear: missing chat id", 1400);
      return Result.none();
    }
    VnPhoneData data = VnPhoneStateStore.load(state, seedSupplier);
    data.clearMessages(tokens.get(1));
    VnPhoneStateStore.save(state, data);
    return Result.none();
  }

  private static String collectMessageText(List<String> tokens, int start, List<KeyValue> options) {
    List<String> textParts = new ArrayList<>();
    for (int i = start; i < tokens.size(); i++) {
      KeyValue option = parseOption(tokens.get(i));
      if (option != null) {
        options.add(option);
      } else {
        textParts.add(tokens.get(i));
      }
    }
    return String.join(" ", textParts).trim();
  }

  private static List<KeyValue> parseOptions(List<String> tokens, int start) {
    List<KeyValue> options = new ArrayList<>();
    for (int i = start; i < tokens.size(); i++) {
      KeyValue option = parseOption(tokens.get(i));
      if (option != null) options.add(option);
    }
    return options;
  }

  private static KeyValue parseOption(String token) {
    if (token == null || token.isBlank()) return null;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int sep = eq > 0 && colon > 0 ? Math.min(eq, colon) : Math.max(eq, colon);
    if (sep <= 0 || sep >= token.length() - 1) return null;
    String key = token.substring(0, sep).trim().toLowerCase(Locale.ROOT);
    String value = token.substring(sep + 1).trim();
    if (key.isEmpty() || value.isEmpty()) return null;
    return new KeyValue(key, value);
  }

  private static boolean parseBoolean(String token, boolean fallback) {
    if (token == null || token.isBlank()) return fallback;
    return switch (token.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on" -> true;
      case "false", "0", "no", "off" -> false;
      default -> fallback;
    };
  }

  private static List<String> splitCsv(String csv) {
    List<String> values = new ArrayList<>();
    if (csv == null || csv.isBlank()) return values;
    for (String token : csv.split(",")) {
      String normalized = VnPhoneData.normalizeId(token);
      if (normalized != null && !values.contains(normalized)) {
        values.add(normalized);
      }
    }
    return values;
  }

  private record KeyValue(String key, String value) {
  }
}
