package com.jvn.core.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhoneSceneTest {

  @Test
  void appHomeSelectionCanOpenChatsAndCalls() {
    VnPhoneData data = new VnPhoneData();
    data.setHomeMode(VnPhoneData.HomeMode.APPS);
    data.getOrCreateChat("mc_lily").setTitle("Lily");
    data.getOrCreateCall("lily_video").setTitle("Lily Video");

    VnPhoneData.PhoneApp messages = data.getOrCreateApp("messages");
    messages.setTargetType(VnPhoneData.AppTargetType.CHAT);
    messages.setTargetValue("mc_lily");

    VnPhoneData.PhoneApp call = data.getOrCreateApp("call");
    call.setTargetType(VnPhoneData.AppTargetType.CALL);
    call.setTargetValue("lily_video");

    PhoneScene scene = new PhoneScene(null, data, ignored -> { });
    scene.setSelectedHomeIndex(0);
    scene.openSelectedHomeEntry();

    assertTrue(scene.isShowingChat());
    assertEquals("mc_lily", scene.getCurrentChatId());

    scene.showHome();
    scene.setSelectedHomeIndex(1);
    scene.openSelectedHomeEntry();

    assertTrue(scene.isShowingCall());
    assertEquals("lily_video", scene.getCurrentCallId());
  }
}
