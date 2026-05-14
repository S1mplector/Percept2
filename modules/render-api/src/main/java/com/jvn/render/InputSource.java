package com.jvn.render;

/**
 * Callback interface for platform input events (keyboard, mouse, touch).
 *
 * <p>Platform launchers (FX, Android, iOS, Web) implement this to route
 * keyboard/mouse/touch events to the engine's input system.
 * The engine provides an instance to the platform at startup and calls
 * {@code onKeyEvent}, {@code onMouseEvent}, or {@code onTouchEvent} as appropriate.</p>
 */
public interface InputSource {

  /**
   * Deliver a keyboard event.
   *
   * @param keyCode  platform-specific key code (e.g., VK_LEFT in Swing, KeyEvent.DOM_VK_LEFT in web)
   * @param keyChar  the character represented by this key (if text input), or '\0' if not applicable
   * @param pressed  true for key-down, false for key-up
   */
  default void onKeyEvent(int keyCode, char keyChar, boolean pressed) {}

  /**
   * Deliver a mouse or pointer event.
   *
   * @param x        logical X coordinate within the render surface
   * @param y        logical Y coordinate within the render surface
   * @param button   mouse button (0 = left, 1 = middle, 2 = right, -1 = move/hover with no button)
   * @param pressed  true for button-down, false for button-up (ignored when button == -1)
   */
  default void onMouseEvent(double x, double y, int button, boolean pressed) {}

  /**
   * Deliver a touch event (multi-touch or single-touch).
   *
   * @param touches array of touch points; each element is [x, y, touchId]
   *                (logical coordinates within the render surface)
   */
  default void onTouchEvent(double[][] touches) {}

  /**
   * Deliver a scroll/wheel event.
   *
   * @param x      logical X coordinate
   * @param y      logical Y coordinate
   * @param deltaX horizontal scroll amount (negative = left, positive = right)
   * @param deltaY vertical scroll amount (negative = up, positive = down)
   */
  default void onScrollEvent(double x, double y, double deltaX, double deltaY) {}
}
