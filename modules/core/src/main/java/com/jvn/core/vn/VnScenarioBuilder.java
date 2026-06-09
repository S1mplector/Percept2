package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.List;

import com.jvn.core.animation.Easing;
import com.jvn.core.vn.stage.VnStagePreset;

/**
 * Fluent builder for creating VN scenarios programmatically
 */
public class VnScenarioBuilder {
  private final VnScenario.Builder scenarioBuilder;
  private String lastLabel = null;

  public VnScenarioBuilder(String scenarioId) {
    this.scenarioBuilder = VnScenario.builder(scenarioId);
  }

  public VnScenarioBuilder addCharacter(String id, String displayName) {
    VnCharacter character = VnCharacter.builder(id)
      .displayName(displayName)
      .build();
    scenarioBuilder.addCharacter(character);
    return this;
  }

  public VnScenarioBuilder addCharacterWithExpressions(String id, String displayName, 
                                                       String... expressionPaths) {
    VnCharacter.Builder builder = VnCharacter.builder(id).displayName(displayName);
    
    // First path is neutral, rest are named by index
    if (expressionPaths.length > 0) {
      builder.addExpression("neutral", expressionPaths[0]);
      for (int i = 1; i < expressionPaths.length; i++) {
        builder.addExpression("expression" + i, expressionPaths[i]);
      }
    }
    
    scenarioBuilder.addCharacter(builder.build());
    return this;
  }

  /**
   * Add a fully built character. If a character with the same id already exists, it will be replaced.
   */
  public VnScenarioBuilder addCharacter(VnCharacter character) {
    scenarioBuilder.addCharacter(character);
    return this;
  }

  public VnScenarioBuilder addBackground(String id, String imagePath) {
    scenarioBuilder.addBackground(new VnBackground(id, imagePath));
    return this;
  }

  public VnScenarioBuilder addStagePreset(VnStagePreset stagePreset) {
    scenarioBuilder.addStagePreset(stagePreset);
    return this;
  }

  public VnScenarioBuilder label(String labelName) {
    this.lastLabel = labelName;
    scenarioBuilder.addLabel(labelName);
    return this;
  }

  public VnScenarioBuilder background(String backgroundId) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.BACKGROUND)
        .backgroundId(backgroundId)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder dialogue(String speaker, String text) {
    return dialogue(speaker, text, null);
  }

  public VnScenarioBuilder dialogue(String speaker, String text, String voiceTrackId) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(DialogueLine.builder()
          .speakerName(speaker)
          .text(text)
          .voiceTrackId(voiceTrackId)
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder dialogue(String speaker, String text, String characterId, 
                                    String expression, CharacterPosition position) {
    return dialogue(speaker, text, characterId, expression, position, null);
  }

  public VnScenarioBuilder dialogue(String speaker, String text, String characterId,
                                    String expression, CharacterPosition position,
                                    String voiceTrackId) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(DialogueLine.builder()
          .speakerName(speaker)
          .text(text)
          .characterId(characterId)
          .expression(expression)
          .position(position)
          .voiceTrackId(voiceTrackId)
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder dialogue(DialogueLine line) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(line)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder choice(String... choices) {
    List<Choice> choiceList = new ArrayList<>();
    for (String choiceText : choices) {
      choiceList.add(Choice.builder().text(choiceText).build());
    }
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.CHOICE)
        .choices(choiceList)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder choiceWithTargets(String[][] choiceData) {
    // choiceData format: [["choice text", "target label"], ...]
    List<Choice> choiceList = new ArrayList<>();
    for (String[] data : choiceData) {
      String text = data[0];
      String target = data.length > 1 ? data[1] : null;
      choiceList.add(Choice.builder().text(text).targetLabel(target).build());
    }
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.CHOICE)
        .choices(choiceList)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder choiceNodes(List<Choice> choiceList) {
    if (choiceList == null) choiceList = new ArrayList<>();
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.CHOICE)
        .choices(choiceList)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder choice(Choice... choices) {
    return choiceNodes(choices == null ? new ArrayList<>() : java.util.Arrays.asList(choices));
  }

  public VnScenarioBuilder jump(String labelName) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.JUMP)
        .jumpLabel(labelName)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder end() {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.END).build()
    );
    return this;
  }

  public VnScenario build() {
    return scenarioBuilder.build();
  }

  // --- Enhancements below: actions and timing ---

  public VnScenarioBuilder show(String characterId, String expression, CharacterPosition position) {
    return show(characterId, expression, position, null);
  }

  public VnScenarioBuilder show(String characterId, String expression, CharacterPosition position, Integer layerOrder) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.SHOW)
        .characterToShow(characterId)
        .showExpression(expression)
        .showPosition(position)
        .showLayerOrder(layerOrder)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder hide(String characterId) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.HIDE)
        .characterToHide(characterId)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder move(String characterId, CharacterPosition position, String expression,
                                 Easing.Type easingType, long durationMs) {
    VnNode.Builder b = VnNode.builder(VnNodeType.MOVE)
        .characterToShow(characterId)
        .showPosition(position);
    if (expression != null) b.showExpression(expression);
    if (easingType != null) b.moveEasingType(easingType);
    if (durationMs > 0) b.moveDurationMs(durationMs);
    scenarioBuilder.addNode(b.build());
    return this;
  }

  public VnScenarioBuilder waitMs(long ms) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.WAIT)
        .waitMs(ms)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder playBgm(String trackId, boolean loop) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.AUDIO)
        .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.PLAY_BGM)
          .trackId(trackId)
          .loop(loop)
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder stopBgm() {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.AUDIO)
        .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.STOP_BGM).build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder fadeOutBgm() {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.AUDIO)
        .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.FADE_OUT_BGM).build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder fadeOutBgm(long durationMs) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.AUDIO)
        .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.FADE_OUT_BGM)
          .durationMs(Math.max(0, durationMs))
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder playSfx(String trackId) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.AUDIO)
        .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.PLAY_SFX)
          .trackId(trackId)
          .loop(false)
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder playVoice(String trackId) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.AUDIO)
        .audioCommand(VnAudioCommand.builder(VnAudioCommand.AudioCommandType.PLAY_VOICE)
          .trackId(trackId)
          .loop(false)
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder transition(VnTransition.TransitionType type, long durationMs, String targetBackgroundId) {
    return transition(type, durationMs, targetBackgroundId, null);
  }

  public VnScenarioBuilder transition(VnTransition.TransitionType type, long durationMs, String targetBackgroundId, String maskAssetPath) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.TRANSITION)
        .transition(VnTransition.builder(type)
          .durationMs(durationMs)
          .targetBackgroundId(targetBackgroundId)
          .maskAssetPath(maskAssetPath)
          .build())
        .build()
    );
    return this;
  }

  // --- Subroutine support ---

  public VnScenarioBuilder call(String labelName) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.CALL)
        .jumpLabel(labelName)
        .build()
    );
    return this;
  }

  public VnScenarioBuilder returnFromCall() {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.RETURN)
        .build()
    );
    return this;
  }

  // --- Particle effects ---
  public VnScenarioBuilder particles(VnParticleCommand cmd) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.PARTICLE)
        .particleCommand(cmd)
        .build()
    );
    return this;
  }

  // --- Interop ---
  public VnScenarioBuilder external(String provider, String payload) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.EXTERNAL)
        .external(new VnExternalCommand(provider, payload))
        .build()
    );
    return this;
  }
}
