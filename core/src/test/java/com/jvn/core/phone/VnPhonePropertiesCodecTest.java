package com.jvn.core.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.VnState;

class VnPhonePropertiesCodecTest {

  @Test
  void roundTripsContactsChatsAndMessages() throws Exception {
    VnPhoneData data = new VnPhoneData();
    data.setTitle("Accord");
    data.setSubtitle("Unread chats");
    data.setWallpaperPath("assets/ui/phone_wallpaper.png");
    data.setAccentColor("#80bfff");

    VnPhoneData.Contact mc = data.getOrCreateContact("mc");
    mc.setDisplayName("John");
    mc.setSelf(true);

    VnPhoneData.Contact lily = data.getOrCreateContact("ll");
    lily.setDisplayName("Lily");
    lily.setAvatarPath("assets/phone/lily.png");

    VnPhoneData.Chat chat = data.getOrCreateChat("mc_lily");
    chat.setTitle("LostVarnacola");
    chat.setIconPath("assets/phone/lily.png");
    chat.addParticipant("mc");
    chat.addParticipant("ll");
    data.appendMessage("mc_lily", "ll", "You awake?", "08:14", true);
    data.appendMessage("mc_lily", "mc", "Barely.", "08:15", false);

    String encoded = VnPhonePropertiesCodec.toPropertiesString(data);
    VnPhoneData decoded = VnPhonePropertiesCodec.loadFromString(encoded);

    assertEquals("Accord", decoded.getTitle());
    assertEquals("Unread chats", decoded.getSubtitle());
    assertEquals("assets/ui/phone_wallpaper.png", decoded.getWallpaperPath());
    assertEquals("Lily", decoded.getContact("ll").getDisplayName());
    assertTrue(decoded.getContact("mc").isSelf());
    assertEquals(1, decoded.orderedChats().size());
    VnPhoneData.Chat decodedChat = decoded.getChat("mc_lily");
    assertNotNull(decodedChat);
    assertEquals("LostVarnacola", decodedChat.getTitle());
    assertEquals(2, decodedChat.getMessages().size());
    assertEquals("You awake?", decodedChat.getMessages().get(0).getText());
    assertEquals("08:15", decodedChat.getMessages().get(1).getTimeText());
    assertFalse(decodedChat.isUnread(), "last append marked the chat as read");
  }

  @Test
  void stateStoreLoadsSeedAndPersistsBackIntoVariables() {
    VnState state = new VnState();

    VnPhoneData loaded = VnPhoneStateStore.load(state, () -> {
      VnPhoneData seed = new VnPhoneData();
      seed.getOrCreateContact("mc").setDisplayName("John");
      seed.appendMessage("thread_a", "mc", "seed", "09:00", false);
      return seed;
    });

    assertEquals("seed", loaded.getChat("thread_a").getLastMessage().getText());

    loaded.appendMessage("thread_a", "mc", "persisted", "09:01", false);
    VnPhoneStateStore.save(state, loaded);

    Object raw = state.getVariable(VnPhoneStateStore.VAR_PHONE_PROPERTIES);
    assertTrue(raw instanceof String);
    VnPhoneData decoded = VnPhoneStateStore.load(state, VnPhoneData::new);
    assertEquals(2, decoded.getChat("thread_a").getMessages().size());
    assertEquals("persisted", decoded.getChat("thread_a").getLastMessage().getText());
  }
}
