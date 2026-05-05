package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.phone.PhoneScene;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhoneStateStore;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnScene;

class RuntimeVnInteropPhoneTest {

  @Test
  void phoneCommandsPersistStateAndOpenChatScene() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    RuntimeVnInterop interop = new RuntimeVnInterop(engine);

    VnScene current = new VnScene(new VnScenarioBuilder("route").label("start").end().build());
    current.getState().setSourceScriptName("story/phone_test.vns");

    interop.handle(new VnExternalCommand("phone", "contact mc name=\"John\" self=true"), current);
    interop.handle(new VnExternalCommand("phone", "contact ll name=\"Lily\" avatar=\"assets/phone/lily.png\""), current);
    interop.handle(new VnExternalCommand("phone", "thread mc_lily title=\"LostVarnacola\" participants=mc,ll"), current);
    interop.handle(new VnExternalCommand("phone", "message mc_lily ll \"You awake?\" time=08:14"), current);
    interop.handle(new VnExternalCommand("phone", "chat mc_lily"), current);

    Object raw = current.getState().getVariable(VnPhoneStateStore.VAR_PHONE_PROPERTIES);
    assertTrue(raw instanceof String && !((String) raw).isBlank(), "phone data should be persisted into VN variables");

    PhoneScene phone = assertInstanceOf(PhoneScene.class, engine.scenes().peek());
    assertEquals(current, phone.getVnScene());
    assertEquals("mc_lily", phone.getCurrentChatId());

    VnPhoneData.Chat chat = phone.getCurrentChat();
    assertNotNull(chat);
    assertEquals("LostVarnacola", chat.getTitle());
    assertEquals(1, chat.getMessages().size());
    assertEquals("You awake?", chat.getMessages().get(0).getText());
    assertEquals("08:14", chat.getMessages().get(0).getTimeText());
    assertEquals("Lily", phone.getData().getContact("ll").getDisplayName());
  }
}
