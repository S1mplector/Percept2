package com.jvn.core.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
    data.setSkinId("sms");
    data.setSkinBackgroundPath("assets/ui/phone/skins/sms/sms_background.png");
    data.setSkinTopBarPath("assets/ui/phone/skins/sms/sms_top_bar.png");
    data.setSkinBottomBarPath("assets/ui/phone/skins/sms/sms_bottom_bar.png");
    data.setSkinMessageFieldPath("assets/ui/phone/skins/sms/sms_message_field.png");
    data.setSkinNavLeadingPath("assets/ui/phone/skins/sms/sms_back_arrow.png");
    data.setSkinNavTrailingPrimaryPath("assets/ui/phone/skins/sms/sms_video_call_button.png");
    data.setSkinComposerTrailingPrimaryPath("assets/ui/phone/skins/sms/sms_record_voice_message_button.png");
    data.setIncomingBubbleImagePath("assets/ui/phone/skins/sms/speech bubbles/sms_their_message_-_FULL.png");
    data.setOutgoingBubbleImagePath("assets/ui/phone/skins/sms/speech bubbles/sms_your_message_FULL.png");

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
    assertEquals("sms", decoded.getSkinId());
    assertEquals("assets/ui/phone/skins/sms/sms_top_bar.png", decoded.getSkinTopBarPath());
    assertEquals("assets/ui/phone/skins/sms/speech bubbles/sms_their_message_-_FULL.png", decoded.getIncomingBubbleImagePath());
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

  @Test
  void supportsLegacyBubbleImagePropertyAliases() throws Exception {
    String props = """
        app.skin=discord
        app.bubbleIncomingImage=assets/ui/phone/skins/discord/incoming.png
        app.bubbleOutgoingImage=assets/ui/phone/skins/discord/outgoing.png
        """;

    VnPhoneData decoded = VnPhonePropertiesCodec.loadFromString(props);
    assertEquals("discord", decoded.getSkinId());
    assertEquals("assets/ui/phone/skins/discord/incoming.png", decoded.getIncomingBubbleImagePath());
    assertEquals("assets/ui/phone/skins/discord/outgoing.png", decoded.getOutgoingBubbleImagePath());
  }

  @Test
  void roundTripsTypedMessagesAppsCallsAndStatus() throws Exception {
    VnPhoneData data = new VnPhoneData();
    data.setHomeMode(VnPhoneData.HomeMode.APPS);
    data.setStatusTimeText("08:14");
    data.setStatusModeText("Focus");
    data.setStatusSignalText("5G");
    data.setStatusBatteryText("82%");

    VnPhoneData.Contact lily = data.getOrCreateContact("lily");
    lily.setDisplayName("Lily");

    VnPhoneData.Chat chat = data.getOrCreateChat("mc_lily");
    chat.setComposerText("typing...");
    chat.setComposerHint("Message");
    data.appendMessage("mc_lily", "lily", null, "08:14", VnPhoneData.MessageType.IMAGE,
        "assets/phone/messages/lily_photo.png", "Look at this", null, List.of(), true);
    data.appendMessage("mc_lily", "lily", null, "08:15", VnPhoneData.MessageType.AUDIO,
        "assets/audio/voice/lily_note.ogg", "Voice note", "0:12", List.of(), true);
    data.appendMessage("mc_lily", null, "Friday, 8 March", null, VnPhoneData.MessageType.DATE,
        null, null, null, List.of(), false);
    data.appendMessage("mc_lily", "lily", "Choose one", null, VnPhoneData.MessageType.MENU,
        null, null, null, List.of("Meet now", "Later"), false);

    VnPhoneData.PhoneApp app = data.getOrCreateApp("messages");
    app.setTitle("Messages");
    app.setIconPath("assets/ui/phone/apps/messages.png");
    app.setBadgeText("3");
    app.setPage(1);
    app.setTargetType(VnPhoneData.AppTargetType.CHAT);
    app.setTargetValue("mc_lily");

    VnPhoneData.Call call = data.getOrCreateCall("lily_video");
    call.setTitle("Lily");
    call.setSubtitle("LostVarnacola");
    call.setParticipantId("lily");
    call.setAvatarPath("assets/phone/contacts/lily.png");
    call.setStatusText("Connecting...");
    call.setVideo(true);

    String encoded = VnPhonePropertiesCodec.toPropertiesString(data);
    VnPhoneData decoded = VnPhonePropertiesCodec.loadFromString(encoded);

    assertEquals(VnPhoneData.HomeMode.APPS, decoded.getHomeMode());
    assertEquals("08:14", decoded.getStatusTimeText());
    assertEquals("Focus", decoded.getStatusModeText());
    assertEquals("5G", decoded.getStatusSignalText());
    assertEquals("82%", decoded.getStatusBatteryText());
    assertEquals("typing...", decoded.getChat("mc_lily").getComposerText());
    assertEquals("Message", decoded.getChat("mc_lily").getComposerHint());
    assertEquals(VnPhoneData.MessageType.IMAGE, decoded.getChat("mc_lily").getMessages().get(0).getType());
    assertEquals("assets/phone/messages/lily_photo.png", decoded.getChat("mc_lily").getMessages().get(0).getAssetPath());
    assertEquals(VnPhoneData.MessageType.AUDIO, decoded.getChat("mc_lily").getMessages().get(1).getType());
    assertEquals("0:12", decoded.getChat("mc_lily").getMessages().get(1).getDurationText());
    assertEquals(VnPhoneData.MessageType.DATE, decoded.getChat("mc_lily").getMessages().get(2).getType());
    assertEquals(List.of("Meet now", "Later"), decoded.getChat("mc_lily").getMessages().get(3).getOptions());
    assertEquals("Messages", decoded.getApp("messages").getTitle());
    assertEquals(1, decoded.getApp("messages").getPage());
    assertEquals(VnPhoneData.AppTargetType.CHAT, decoded.getApp("messages").getTargetType());
    assertEquals("mc_lily", decoded.getApp("messages").getTargetValue());
    assertEquals("Lily", decoded.getCall("lily_video").getTitle());
    assertTrue(decoded.getCall("lily_video").isVideo());
    assertEquals("lily", decoded.getCall("lily_video").getParticipantId());
  }
}
