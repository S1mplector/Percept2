package com.jvn.core.vn;

/**
 * Types of nodes in a visual novel script.
 * Each type represents a distinct operation in the VN execution model.
 */
public enum VnNodeType {
  // Interactive nodes (require player input or wait)
  DIALOGUE,      // Display dialogue text
  CHOICE,        // Present choices to the player

  // Scene control
  BACKGROUND,    // Change background
  TRANSITION,    // Scene transition effect

  // Character control
  SHOW,          // Show a character at a position
  HIDE,          // Hide a character

  // Flow control
  JUMP,          // Jump to a label
  CALL,          // Call a subroutine label (pushes return address)
  RETURN,        // Return from subroutine call

  // Timing
  WAIT,          // Wait for a duration

  // Audio
  AUDIO,         // Play/stop/fade audio (BGM/SFX/Voice)

  // External integration
  EXTERNAL,      // External interop call (jes/java/custom)

  // Terminal
  END;           // End of scenario

  /**
   * Returns true if this node type requires player input to advance.
   */
  public boolean isInteractive() {
    return this == DIALOGUE || this == CHOICE;
  }

  /**
   * Returns true if this node type executes instantly and chains to the next node.
   */
  public boolean isInstant() {
    return this == BACKGROUND || this == SHOW || this == HIDE || 
           this == JUMP || this == CALL || this == RETURN || this == AUDIO;
  }

  /**
   * Returns true if this node type blocks progression for a duration.
   */
  public boolean isBlocking() {
    return this == WAIT || this == TRANSITION;
  }
}
