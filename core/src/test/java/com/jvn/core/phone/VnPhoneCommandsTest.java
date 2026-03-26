package com.jvn.core.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnScenario;

class VnPhoneCommandsTest {

  @Test
  void opensHomeChatAndCallTargets() {
    VnScene scene = scene();

    assertEquals(VnPhoneCommands.Result.openHome(), VnPhoneCommands.handle("open", scene, VnPhoneData::new));
    assertEquals(VnPhoneCommands.Result.openHome(), VnPhoneCommands.handle("open home", scene, VnPhoneData::new));
    assertEquals(VnPhoneCommands.Result.openChat("mc_lily"), VnPhoneCommands.handle("open chat mc_lily", scene, VnPhoneData::new));
    assertEquals(VnPhoneCommands.Result.openChat("mc_lily"), VnPhoneCommands.handle("chat mc_lily", scene, VnPhoneData::new));
    assertEquals(VnPhoneCommands.Result.openCall("lily_video"), VnPhoneCommands.handle("open call lily_video", scene, VnPhoneData::new));
    assertEquals(VnPhoneCommands.Result.openCall("lily_video"), VnPhoneCommands.handle("call lily_video", scene, VnPhoneData::new));
    assertEquals(VnPhoneCommands.Result.close(), VnPhoneCommands.handle("close", scene, VnPhoneData::new));
  }

  @Test
  void mutatesPhoneDataAcrossContactsThreadsAppsMessagesAndCalls() {
    VnScene scene = scene();

    VnPhoneCommands.handle("contact ll name=\"Lily\" avatar=assets/phone/lily.png color=#ff99cc", scene, VnPhoneData::new);
    VnPhoneCommands.handle("thread mc_lily title=\"LostVarnacola\" participants=mc,ll icon=assets/phone/lily.png unread=true composer=\"typing...\" composerHint=Message", scene, VnPhoneData::new);
    VnPhoneCommands.handle("app messages title=Messages icon=assets/ui/phone/apps/messages.png badge=3 page=1 chat=mc_lily accent=#89dceb", scene, VnPhoneData::new);
    VnPhoneCommands.handle("message mc_lily ll type=image asset=assets/phone/messages/lily_photo.png caption=\"Look at this\" time=08:14 unread=false", scene, VnPhoneData::new);
    VnPhoneCommands.handle("message mc_lily ll type=audio asset=assets/audio/voice/lily_note.ogg caption=\"Voice note\" duration=0:12 time=08:15", scene, VnPhoneData::new);
    VnPhoneCommands.handle("message mc_lily type=date \"Friday, 8 March\"", scene, VnPhoneData::new);
    VnPhoneCommands.handle("message mc_lily ll \"Choose one\" menu=\"Meet now|Later\" unread=false", scene, VnPhoneData::new);
    VnPhoneCommands.handle("unread mc_lily true", scene, VnPhoneData::new);

    VnPhoneCommands.Result callResult = VnPhoneCommands.handle(
        "call lily_video title=Lily subtitle=LostVarnacola participant=ll avatar=assets/phone/contacts/lily.png status=\"Connecting...\" video=true open=true",
        scene,
        VnPhoneData::new);

    VnPhoneData data = VnPhoneStateStore.load(scene.getState(), VnPhoneData::new);
    VnPhoneData.Contact lily = data.getContact("ll");
    VnPhoneData.Chat chat = data.getChat("mc_lily");
    VnPhoneData.PhoneApp app = data.getApp("messages");
    VnPhoneData.Call call = data.getCall("lily_video");

    assertEquals(VnPhoneCommands.Result.openCall("lily_video"), callResult);

    assertEquals("Lily", lily.getDisplayName());
    assertEquals("assets/phone/lily.png", lily.getAvatarPath());
    assertEquals("#ff99cc", lily.getColor());

    assertEquals("LostVarnacola", chat.getTitle());
    assertEquals(List.of("mc", "ll"), chat.getParticipants());
    assertTrue(chat.isUnread());
    assertEquals("typing...", chat.getComposerText());
    assertEquals("Message", chat.getComposerHint());
    assertEquals(4, chat.getMessages().size());

    VnPhoneData.Message image = chat.getMessages().get(0);
    VnPhoneData.Message audio = chat.getMessages().get(1);
    VnPhoneData.Message date = chat.getMessages().get(2);
    VnPhoneData.Message menu = chat.getMessages().get(3);

    assertEquals(VnPhoneData.MessageType.IMAGE, image.getType());
    assertEquals("assets/phone/messages/lily_photo.png", image.getAssetPath());
    assertEquals("Look at this", image.getCaption());
    assertEquals("08:14", image.getTimeText());

    assertEquals(VnPhoneData.MessageType.AUDIO, audio.getType());
    assertEquals("assets/audio/voice/lily_note.ogg", audio.getAssetPath());
    assertEquals("Voice note", audio.getCaption());
    assertEquals("0:12", audio.getDurationText());

    assertEquals(VnPhoneData.MessageType.DATE, date.getType());
    assertEquals("Friday, 8 March", date.getText());
    assertNull(date.getSenderId());

    assertEquals(VnPhoneData.MessageType.MENU, menu.getType());
    assertEquals("Choose one", menu.getText());
    assertEquals(List.of("Meet now", "Later"), menu.getOptions());

    assertEquals("Messages", app.getTitle());
    assertEquals("3", app.getBadgeText());
    assertEquals(1, app.getPage());
    assertEquals(VnPhoneData.AppTargetType.CHAT, app.getTargetType());
    assertEquals("mc_lily", app.getTargetValue());

    assertEquals("Lily", call.getTitle());
    assertEquals("LostVarnacola", call.getSubtitle());
    assertEquals("ll", call.getParticipantId());
    assertEquals("assets/phone/contacts/lily.png", call.getAvatarPath());
    assertEquals("Connecting...", call.getStatusText());
    assertTrue(call.isVideo());
  }

  @Test
  void warnsOnMissingMessagePayload() {
    VnScene scene = scene();

    VnPhoneCommands.Result result = VnPhoneCommands.handle("message mc_lily time=08:14", scene, VnPhoneData::new);

    assertEquals(VnPhoneCommands.Result.none(), result);
    assertEquals("phone message: missing text or payload", scene.getState().getHudMessage());
  }

  private static VnScene scene() {
    return new VnScene(VnScenario.builder("phone_commands").build());
  }
}
