package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jvn.core.vn.DialogueLine;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.VnExternalCommand;
import org.junit.jupiter.api.Test;

class VnRendererRetainedDialogueTest {

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
