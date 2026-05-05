package com.jvn.core.phone;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnScene;

/**
 * Modal phone scene layered on top of an active VN scene.
 */
public final class PhoneScene implements Scene {
  private final VnScene vnScene;
  private final VnPhoneData data;
  private final Consumer<VnPhoneData> persistCallback;
  private final boolean homeAccessible;

  private boolean closeRequested;
  private String currentChatId;
  private String currentCallId;
  private int selectedHomeIndex;

  public PhoneScene(VnScene vnScene, VnPhoneData data, Consumer<VnPhoneData> persistCallback) {
    this(vnScene, data, persistCallback, null);
  }

  public PhoneScene(VnScene vnScene,
                    VnPhoneData data,
                    Consumer<VnPhoneData> persistCallback,
                    String initialChatId) {
    this.vnScene = vnScene;
    this.data = data == null ? new VnPhoneData() : data;
    this.persistCallback = persistCallback == null ? ignored -> { } : persistCallback;
    this.homeAccessible = initialChatId == null || initialChatId.isBlank();
    if (initialChatId != null && !initialChatId.isBlank()) {
      openChat(initialChatId);
    } else {
      this.currentChatId = null;
      this.currentCallId = null;
      this.selectedHomeIndex = 0;
    }
  }

  public VnScene getVnScene() {
    return vnScene;
  }

  public VnPhoneData getData() {
    return data;
  }

  public List<VnPhoneData.Chat> getOrderedChats() {
    return data.orderedChats();
  }

  public List<VnPhoneData.PhoneApp> getOrderedApps() {
    return data.orderedApps();
  }

  public boolean isShowingHome() {
    return currentChatId == null && currentCallId == null;
  }

  public boolean canReturnHome() {
    return homeAccessible;
  }

  public boolean isShowingChat() {
    return currentChatId != null;
  }

  public boolean isShowingCall() {
    return currentCallId != null;
  }

  public String getCurrentChatId() {
    return currentChatId;
  }

  public String getCurrentCallId() {
    return currentCallId;
  }

  public VnPhoneData.Chat getCurrentChat() {
    return currentChatId == null ? null : data.getChat(currentChatId);
  }

  public VnPhoneData.Call getCurrentCall() {
    return currentCallId == null ? null : data.getCall(currentCallId);
  }

  public int getSelectedHomeIndex() {
    return selectedHomeIndex;
  }

  public void setSelectedHomeIndex(int selectedHomeIndex) {
    int max = Math.max(0, getHomeEntryCount() - 1);
    this.selectedHomeIndex = Math.max(0, Math.min(selectedHomeIndex, max));
  }

  public int getHomeEntryCount() {
    return data.getHomeMode() == VnPhoneData.HomeMode.APPS
        ? getOrderedApps().size()
        : getOrderedChats().size();
  }

  public void moveSelection(int delta) {
    if (!isShowingHome()) return;
    int count = getHomeEntryCount();
    if (count <= 0) {
      selectedHomeIndex = 0;
      return;
    }
    int next = selectedHomeIndex + delta;
    if (next < 0) next = count - 1;
    if (next >= count) next = 0;
    selectedHomeIndex = next;
  }

  public void openSelectedHomeEntry() {
    if (data.getHomeMode() == VnPhoneData.HomeMode.APPS) {
      List<VnPhoneData.PhoneApp> apps = getOrderedApps();
      if (apps.isEmpty()) return;
      VnPhoneData.PhoneApp app = apps.get(Math.max(0, Math.min(selectedHomeIndex, apps.size() - 1)));
      if (app != null) activateApp(app);
      return;
    }
    openSelectedChat();
  }

  public void openSelectedChat() {
    List<VnPhoneData.Chat> chats = getOrderedChats();
    if (chats.isEmpty()) return;
    VnPhoneData.Chat chat = chats.get(Math.max(0, Math.min(selectedHomeIndex, chats.size() - 1)));
    if (chat != null) openChat(chat.getId());
  }

  public void openChat(String chatId) {
    String normalized = VnPhoneData.normalizeId(chatId);
    if (normalized == null) return;
    VnPhoneData.Chat chat = data.getOrCreateChat(normalized);
    if (chat.getTitle() == null || chat.getTitle().isBlank()) {
      chat.setTitle(data.defaultChatTitle(chat));
    }
    currentCallId = null;
    currentChatId = normalized;
    chat.setUnread(false);
    syncSelectionWithChat(normalized);
    persist();
  }

  public void openCall(String callId) {
    String normalized = VnPhoneData.normalizeId(callId);
    if (normalized == null) return;
    VnPhoneData.Call call = data.getOrCreateCall(normalized);
    currentChatId = null;
    currentCallId = normalized;
    if (call.getParticipantId() != null) {
      VnPhoneData.Contact contact = data.getOrCreateContact(call.getParticipantId());
      if (contact.getDisplayName() == null || contact.getDisplayName().isBlank()) {
        contact.setDisplayName(contact.getId());
      }
    }
    persist();
  }

  public void showHome() {
    currentChatId = null;
    currentCallId = null;
  }

  public void back() {
    if ((isShowingChat() || isShowingCall()) && homeAccessible) {
      showHome();
      return;
    }
    closeRequested = true;
  }

  public void requestClose() {
    closeRequested = true;
  }

  public boolean consumeCloseRequested() {
    boolean result = closeRequested;
    closeRequested = false;
    return result;
  }

  public void markCurrentChatRead() {
    VnPhoneData.Chat chat = getCurrentChat();
    if (chat == null || !chat.isUnread()) return;
    chat.setUnread(false);
    persist();
  }

  public void persist() {
    persistCallback.accept(data);
  }

  private void activateApp(VnPhoneData.PhoneApp app) {
    if (app == null) return;
    switch (app.getTargetType()) {
      case CHAT -> openChat(app.getTargetValue());
      case CALL -> openCall(app.getTargetValue());
      case HOME, NONE, LABEL -> showHome();
    }
  }

  private void syncSelectionWithChat(String chatId) {
    if (chatId == null) return;
    List<VnPhoneData.Chat> chats = getOrderedChats();
    for (int i = 0; i < chats.size(); i++) {
      VnPhoneData.Chat chat = chats.get(i);
      if (chat != null && Objects.equals(chat.getId(), chatId)) {
        selectedHomeIndex = i;
        return;
      }
    }
  }

  @Override
  public void update(long deltaMs) {
  }
}
