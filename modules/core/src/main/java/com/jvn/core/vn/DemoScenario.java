package com.jvn.core.vn;

/**
 * Factory for creating demo VN scenarios for testing
 */
public class DemoScenario {
  
  /**
   * Creates a simple demo scenario with dialogue and choices
   */
  public static VnScenario createSimpleDemo() {
    VnScenarioBuilder builder = new VnScenarioBuilder("demo");
    
    // Add characters
    builder.addCharacter("narrator", "Narrator");
    builder.addCharacter("alice", "Alice");
    builder.addCharacter("bob", "Bob");
    
    // Add backgrounds (use existing placeholder asset)
    builder.addBackground("room", "game/images/placeholder.png");
    builder.addBackground("forest", "game/images/placeholder.png");
    
    // Build scenario
    return builder
      .background("room")
      .dialogue("Narrator", "Welcome to Percept4 Engine - Visual Novel Demo!")
      .dialogue("Narrator", "This is a simple demonstration of the visual novel system.")
      .dialogue("Alice", "Hi! I'm Alice. Nice to meet you!", "alice", "neutral", CharacterPosition.LEFT)
      .dialogue("Bob", "And I'm Bob. Welcome!", "bob", "neutral", CharacterPosition.RIGHT)
      .dialogue("Alice", "So, what do you think of our engine?", "alice", "neutral", CharacterPosition.LEFT)
      .choiceWithTargets(new String[][] {
        {"It's amazing!", "choice_amazing"},
        {"Tell me more", "choice_more"}
      })
      
      .label("choice_amazing")
      .dialogue("Bob", "That's great to hear!", "bob", "neutral", CharacterPosition.RIGHT)
      .dialogue("Alice", "We're glad you like it!", "alice", "neutral", CharacterPosition.LEFT)
      .jump("ending")
      
      .label("choice_more")
      .dialogue("Alice", "Well, Percept4 is a full 3D game engine...", "alice", "neutral", CharacterPosition.LEFT)
      .dialogue("Bob", "But we're starting with visual novel features!", "bob", "neutral", CharacterPosition.RIGHT)
      .dialogue("Narrator", "The engine supports dialogue, choices, character sprites, and more.")
      
      .label("ending")
      .background("forest")
      .dialogue("Narrator", "This concludes our demo. Thanks for trying Percept4!")
      .end()
      .build();
  }
  
  /**
   * Creates a minimal test scenario
   */
  public static VnScenario createMinimalTest() {
    VnScenarioBuilder builder = new VnScenarioBuilder("minimal");
    
    builder.addCharacter("narrator", "Narrator");
    builder.addBackground("default", "game/images/placeholder.png");
    
    return builder
      .background("default")
      .dialogue("Narrator", "This is a minimal test scenario.")
      .dialogue("Narrator", "If you can read this, the VN system is working!")
      .choice("Continue", "Exit")
      .dialogue("Narrator", "You made a choice. The scenario is now complete.")
      .end()
      .build();
  }
}
