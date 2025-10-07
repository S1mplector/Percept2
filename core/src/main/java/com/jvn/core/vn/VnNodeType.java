package com.jvn.core.vn;

/**
 * Types of nodes in a visual novel script
 */
public enum VnNodeType {
  DIALOGUE,      // Display dialogue text
  CHOICE,        // Present choices to the player
  BACKGROUND,    // Change background
  JUMP,          // Jump to a label
  END            // End of scenario
}
