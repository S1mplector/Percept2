package com.jvn.scenerender.vn;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.jvn.core.vn.Choice;
import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.VnBackground;
import com.jvn.core.vn.VnExternalCommand;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.scenerender.testkit.RecordingBlitter2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class VnRendererTest {

  @Test
  void keepsTheCompletedLineVisibleDuringAnAnimationThenReplacesItWithTheNextLine() {
    DialogueLine first = DialogueLine.builder().text("The animation starts now.").build();
    DialogueLine second = DialogueLine.builder().text("The animation finished.").build();
    VnState state = stateFor(first, second);

    state.advance();
    assertSame(first, VnRenderer.displayedDialogue(state));
    state.advance();
    assertSame(first, VnRenderer.displayedDialogue(state));
    state.advance();
    assertSame(second, VnRenderer.displayedDialogue(state));
  }

  @Test
  void doesNotRenderRetainedDialogueAtAChoiceOrTheEnd() {
    DialogueLine line = DialogueLine.builder().text("Choose carefully.").build();
    VnState choiceState = new VnState();
    choiceState.setScenario(VnScenario.builder("choice")
        .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(line).build())
        .addNode(VnNode.builder(VnNodeType.CHOICE).build())
        .build());
    choiceState.advance();
    assertNull(VnRenderer.displayedDialogue(choiceState));

    VnState endState = new VnState();
    endState.setScenario(VnScenario.builder("end")
        .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(line).build())
        .addNode(VnNode.builder(VnNodeType.END).build())
        .build());
    endState.advance();
    assertNull(VnRenderer.displayedDialogue(endState));
  }

  @Test
  void rendersACompleteFrameWithARealBackgroundAssetWithoutThrowing() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnRenderer renderer = new VnRenderer(blitter);
    VnBackground background = new VnBackground("bg1", "probe/tier1.png");
    VnScenario scenario = VnScenario.builder("smoke")
        .addBackground(background)
        .build();
    VnState state = new VnState();
    state.setScenario(scenario);
    state.setCurrentBackgroundId("bg1");

    assertDoesNotThrow(() -> renderer.render(state, scenario, 800, 600));

    boolean drewRealBackgroundAsset = blitter.calls().stream()
        .anyMatch(c -> "drawImage".equals(c.method()) && "probe/tier1.png".equals(c.args().get(0)));
    assertTrue(drewRealBackgroundAsset,
        "expected the configured background asset path to reach Blitter2D.drawImage directly, "
            + "not fall back to a placeholder because of a broken asset-resolution path (the exact "
            + "bug class sub-project 1's own AssetDimensionProbe gap was)");
  }

  @Test
  void rendersAFrameWithChoicesWithoutThrowingAndExercisesChoiceOverlay() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnRenderer renderer = new VnRenderer(blitter);
    DialogueLine line = DialogueLine.builder().text("Pick one.").build();
    Choice choice1 = Choice.builder().text("Go left").targetLabel("left").build();
    Choice choice2 = Choice.builder().text("Go right").targetLabel("right").build();
    VnScenario scenario = VnScenario.builder("choices")
        .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(line).build())
        .addNode(VnNode.builder(VnNodeType.CHOICE).choices(List.of(choice1, choice2)).build())
        .build();
    VnState state = new VnState();
    state.setScenario(scenario);
    state.advance();

    assertDoesNotThrow(() -> renderer.render(state, scenario, 800, 600));

    int hovered = renderer.getHoveredChoiceIndex(state.getCurrentNode().getChoices(), 800, 600, -100, -100);
    assertTrue(hovered < 0, "far-off-screen mouse position should not hover any choice");
  }

  @Test
  void updateAnimationClearCacheAndDisposeDoNotThrow() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnRenderer renderer = new VnRenderer(blitter);
    assertDoesNotThrow(() -> renderer.updateAnimation(16L));
    assertDoesNotThrow(renderer::clearCache);
    assertDoesNotThrow(renderer::dispose);
  }

  @Test
  void renderErrorOverlayDoesNotThrowWhenNoErrorIsActive() {
    RecordingBlitter2D blitter = new RecordingBlitter2D();
    VnRenderer renderer = new VnRenderer(blitter);
    assertDoesNotThrow(() -> renderer.renderErrorOverlay(null, 800, 600, 0, 0));
  }

  private static VnState stateFor(DialogueLine first, DialogueLine second) {
    VnState state = new VnState();
    state.setScenario(VnScenario.builder("animation_dialogue")
        .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(first).build())
        .addNode(VnNode.builder(VnNodeType.EXTERNAL)
            .external(new VnExternalCommand("jes_timeline", "hero_arrival wait"))
            .build())
        .addNode(VnNode.builder(VnNodeType.WAIT).waitMs(500).build())
        .addNode(VnNode.builder(VnNodeType.DIALOGUE).dialogue(second).build())
        .build());
    return state;
  }
}
