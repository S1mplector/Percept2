package com.jvn.fx.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.render.InputSource;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Adapts JavaFX input events to the platform-agnostic {@code InputSource} interface.
 */
public class FxInputSource implements InputSource {
  private static final Logger log = LoggerFactory.getLogger(FxInputSource.class);

  private final InputSource delegate;

  public FxInputSource(InputSource delegate) {
    this.delegate = delegate;
  }

  public void handleKeyEvent(KeyEvent e) {
    int keyCode = fxKeyCodeToInt(e.getCode());
    char keyChar = e.getText() != null && !e.getText().isEmpty() ? e.getText().charAt(0) : '\0';
    boolean pressed = e.getEventType() == KeyEvent.KEY_PRESSED;
    delegate.onKeyEvent(keyCode, keyChar, pressed);
  }

  public void handleMouseEvent(MouseEvent e) {
    double x = e.getX();
    double y = e.getY();
    int button = fxMouseButtonToInt(e.getButton());
    boolean pressed = e.getEventType() == MouseEvent.MOUSE_PRESSED;

    if (e.getEventType() == MouseEvent.MOUSE_MOVED || e.getEventType() == MouseEvent.MOUSE_ENTERED || e.getEventType() == MouseEvent.MOUSE_EXITED) {
      delegate.onMouseEvent(x, y, -1, false);
    } else {
      delegate.onMouseEvent(x, y, button, pressed);
    }
  }

  public void handleScrollEvent(javafx.scene.input.ScrollEvent e) {
    double x = e.getX();
    double y = e.getY();
    double deltaX = e.getDeltaX();
    double deltaY = e.getDeltaY();
    delegate.onScrollEvent(x, y, deltaX, deltaY);
  }

  private int fxKeyCodeToInt(KeyCode code) {
    // Map JavaFX KeyCode to a consistent integer representation
    // Using the code's ordinal as a stable identifier
    if (code == null) return -1;
    return code.getCode();
  }

  private int fxMouseButtonToInt(MouseButton button) {
    if (button == null) return -1;
    return switch (button) {
      case PRIMARY -> 0;
      case MIDDLE -> 1;
      case SECONDARY -> 2;
      default -> -1;
    };
  }
}
