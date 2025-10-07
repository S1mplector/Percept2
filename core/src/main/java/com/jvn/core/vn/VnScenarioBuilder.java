package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.List;

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

  public VnScenarioBuilder addBackground(String id, String imagePath) {
    scenarioBuilder.addBackground(new VnBackground(id, imagePath));
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
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(DialogueLine.builder()
          .speakerName(speaker)
          .text(text)
          .build())
        .build()
    );
    return this;
  }

  public VnScenarioBuilder dialogue(String speaker, String text, String characterId, 
                                    String expression, CharacterPosition position) {
    scenarioBuilder.addNode(
      VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(DialogueLine.builder()
          .speakerName(speaker)
          .text(text)
          .characterId(characterId)
          .expression(expression)
          .position(position)
          .build())
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
}
