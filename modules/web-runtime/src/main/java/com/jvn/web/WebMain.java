package com.jvn.web;

import org.jspecify.annotations.Nullable;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.events.TouchEvent;
import org.teavm.jso.dom.events.WheelEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.TextRectangle;

/**
 * TeaVM browser entrypoint used by {@code generateJavaScript}.
 */
public final class WebMain {
  static final String CANVAS_ELEMENT_ID = "jvn-canvas";
  static final String CONFIG_ELEMENT_ID = "jvn-config";
  static final String STATUS_ELEMENT_ID = "jvn-status";

  private static @Nullable WebRuntimeSession activeSession;

  private WebMain() {}

  public static void main(String[] args) {
    HTMLDocument document = HTMLDocument.current();
    HTMLElement configElement = document.getElementById(CONFIG_ELEMENT_ID);
    String configJson = configElement == null ? "" : configElement.getTextContent();
    HTMLElement statusElement = document.getElementById(STATUS_ELEMENT_ID);

    try {
      WebRuntimeSession session = WebLauncher.startGame(configJson, CANVAS_ELEMENT_ID);
      activeSession = session;
      document.setTitle(session.engine().getConfig().title() + " — JVN Web");
      setStatus(statusElement, "Engine loop online · Canvas 2D bootstrap");
      attachInputListeners(session);
    } catch (RuntimeException error) {
      setStatus(statusElement, "JVN web startup failed: " + safeMessage(error));
      throw error;
    }
  }

  private static void setStatus(HTMLElement element, String status) {
    if (element != null) element.setInnerText(status);
  }

  private static String safeMessage(RuntimeException error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  /**
   * Attach real browser DOM listeners (keyboard on {@code document}, mouse/wheel/touch on the
   * game canvas) and route them into the engine's {@code Input} state and the session's
   * {@code SceneInputRouter}.
   */
  private static void attachInputListeners(WebRuntimeSession session) {
    HTMLDocument document = HTMLDocument.current();
    HTMLCanvasElement canvas = session.surface().getCanvasElement();
    com.jvn.core.input.ActionMap actionMap = session.actionMap();
    com.jvn.scenerender.input.SceneInputRouter router = session.sceneInputRouter();

    document.addEventListener("keydown", (Event e) -> {
      KeyboardEvent ke = (KeyboardEvent) e;
      com.jvn.core.input.InputCode code = com.jvn.core.input.InputCode.key(canonicalProfileKeyName(ke.getKey()));
      session.engine().input().keyDown(code);
      dispatchAction(actionMap, router, session, code);
    });

    document.addEventListener("keyup", (Event e) -> {
      KeyboardEvent ke = (KeyboardEvent) e;
      session.engine().input().keyUp(
          com.jvn.core.input.InputCode.key(canonicalProfileKeyName(ke.getKey())));
    });

    canvas.addEventListener("mousemove", (Event e) -> {
      MouseEvent me = (MouseEvent) e;
      double[] xy = toSceneCoordinates(canvas, me.getClientX(), me.getClientY());
      session.engine().input().setMousePosition(xy[0], xy[1]);
      router.handleHover(
          session.engine().scenes().peek(), session.surface().getWidth(), session.surface().getHeight(), xy[0], xy[1]);
    });

    canvas.addEventListener("mousedown", (Event e) -> {
      MouseEvent me = (MouseEvent) e;
      session.engine().input().mouseDown(remapDomButton(me.getButton()));
    });

    canvas.addEventListener("mouseup", (Event e) -> {
      MouseEvent me = (MouseEvent) e;
      session.engine().input().mouseUp(remapDomButton(me.getButton()));
    });

    canvas.addEventListener("click", (Event e) -> {
      MouseEvent me = (MouseEvent) e;
      double[] xy = toSceneCoordinates(canvas, me.getClientX(), me.getClientY());
      com.jvn.core.scene.Scene currentScene = session.engine().scenes().peek();
      int errorButtonIndex = router.handleClick(
          currentScene, session.engine(), session.surface().getWidth(), session.surface().getHeight(), xy[0], xy[1]);
      if (errorButtonIndex == 0 && currentScene instanceof com.jvn.core.vn.VnScene vn) {
        vn.clearActiveError();
      }
    });

    canvas.addEventListener("wheel", (Event e) -> {
      WheelEvent we = (WheelEvent) e;
      session.engine().input().addScrollDeltaY(we.getDeltaY());
      we.preventDefault();
    });

    canvas.addEventListener("touchstart", (Event e) -> handleTouch(session, canvas, e, "mousedown"));
    canvas.addEventListener("touchmove", (Event e) -> handleTouch(session, canvas, e, "mousemove"));
    canvas.addEventListener("touchend", (Event e) -> handleTouch(session, canvas, e, "mouseup"));
  }

  /**
   * Synthesize the corresponding mouse action from a single-touch DOM touch event: no new
   * {@code Input} device type, no multi-touch gesture support — a tap behaves like a primary-button
   * click. {@code touchend} carries no live touch point in {@code getTouches()} (the finger has
   * already lifted), so it reuses the engine's last-tracked mouse position instead of re-reading
   * the (possibly-empty) touch list.
   */
  private static void handleTouch(WebRuntimeSession session, HTMLCanvasElement canvas, Event e, String kind) {
    TouchEvent te = (TouchEvent) e;
    te.preventDefault();
    com.jvn.core.input.Input input = session.engine().input();

    switch (kind) {
      case "mousedown" -> {
        if (te.getTouches().getLength() == 0) return;
        var touch = te.getTouches().get(0);
        double[] xy = toSceneCoordinates(canvas, touch.getClientX(), touch.getClientY());
        input.setMousePosition(xy[0], xy[1]);
        input.mouseDown(1);
      }
      case "mousemove" -> {
        if (te.getTouches().getLength() == 0) return;
        var touch = te.getTouches().get(0);
        double[] xy = toSceneCoordinates(canvas, touch.getClientX(), touch.getClientY());
        input.setMousePosition(xy[0], xy[1]);
      }
      case "mouseup" -> {
        input.mouseUp(1);
        com.jvn.scenerender.input.SceneInputRouter router = session.sceneInputRouter();
        com.jvn.core.scene.Scene currentScene = session.engine().scenes().peek();
        int errorButtonIndex = router.handleClick(
            currentScene, session.engine(), session.surface().getWidth(), session.surface().getHeight(),
            input.getMouseX(), input.getMouseY());
        if (errorButtonIndex == 0 && currentScene instanceof com.jvn.core.vn.VnScene vn) {
          vn.clearActiveError();
        }
      }
      default -> { }
    }
  }

  /**
   * Translate a raw DOM {@code KeyboardEvent.key} value into the exact
   * upper-case key-name string {@link com.jvn.core.input.InputActions#defaultProfile()}
   * binds against (JavaFX {@code KeyCode.getName()} naming, since that is what
   * {@code FxLauncher} feeds the same profile on desktop via
   * {@code InputCode.key(e.getCode().getName())}).
   *
   * <p>Most DOM key values coincidentally already match after upper-casing
   * (letters, digits, {@code Enter}→{@code ENTER}, {@code Escape}→{@code ESCAPE},
   * {@code Control}→{@code CONTROL}, {@code F1}..{@code F12}), but several of the
   * profile's bound keys use JavaFX-specific spellings that the DOM spec's
   * {@code KeyboardEvent.key} values do not naturally produce even after
   * upper-casing — those are special-cased explicitly below rather than
   * relying on incidental naming overlap.</p>
   */
  private static String canonicalProfileKeyName(String domKey) {
    if (domKey == null) return "";
    return switch (domKey) {
      case " " -> "SPACE";
      case "ArrowUp" -> "UP";
      case "ArrowDown" -> "DOWN";
      case "ArrowLeft" -> "LEFT";
      case "ArrowRight" -> "RIGHT";
      case "PageUp" -> "PAGE_UP";
      case "PageDown" -> "PAGE_DOWN";
      case "`" -> "BACK_QUOTE";
      default -> domKey.toUpperCase();
    };
  }

  private static int remapDomButton(int domButton) {
    // DOM: 0=primary, 1=middle, 2=secondary. Engine: 1=primary, 2=middle, 3=secondary.
    return switch (domButton) {
      case 0 -> 1;
      case 1 -> 2;
      case 2 -> 3;
      default -> 1;
    };
  }

  private static double[] toSceneCoordinates(HTMLCanvasElement canvas, double clientX, double clientY) {
    TextRectangle rect = canvas.getBoundingClientRect();
    double x = clientX - rect.getLeft();
    double y = clientY - rect.getTop();
    return new double[] { x, y };
  }

  /**
   * Dispatch a just-pressed {@code InputCode} to the matching {@code SceneInputRouter} method.
   * {@code MENU_DELETE}/{@code MENU_RENAME} are intentionally excluded — both use JavaFX-only
   * modal dialogs with no browser equivalent. {@code SETTINGS}/{@code SAVE_MENU} are also
   * intentionally excluded: pushing {@code SettingsScene}/{@code SaveMenuScene} makes them
   * reachable from {@code WebMain.main()}, and TeaVM's whole-program static analysis at
   * {@code generateJavaScript} time walks into their save/settings persistence machinery
   * ({@code VnSaveManager}, {@code VnSaveSerializer}, {@code ActionBindingProfile},
   * {@code ActionBindingProfileStore}), which uses JDK APIs TeaVM's classlib does not support
   * ({@code ObjectInputStream}/{@code ObjectOutputStream}, {@code StringJoiner},
   * {@code BufferedReader.transferTo} via {@code Files.readString}) — TeaVM treats these as hard
   * build errors, breaking {@code :web-runtime:generateJavaScript}/{@code webDist} entirely.
   * Browser settings/save-menu UI was never in this sub-project's scope (see the design spec's
   * exclusions for {@code WebLocalStoragePersistenceBackend}/full persistence UI); a future
   * sub-project can revisit real browser settings/save UI once the persistence path is made
   * TeaVM-safe.
   */
  private static void dispatchAction(
      com.jvn.core.input.ActionMap actionMap,
      com.jvn.scenerender.input.SceneInputRouter router,
      WebRuntimeSession session,
      com.jvn.core.input.InputCode code) {
    com.jvn.core.engine.Engine engine = session.engine();

    if (actionMap.matches(com.jvn.core.input.InputActions.ADVANCE, code)) {
      if (!router.menuEnter(engine)) router.advance(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.MENU_CONFIRM, code)) {
      if (!router.menuEnter(engine)) router.advance(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.SKIP_TOGGLE, code)) {
      router.toggleSkip(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.AUTO_TOGGLE, code)) {
      router.toggleAutoPlay(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.HIDE_UI, code)) {
      router.toggleUI(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.HISTORY, code)) {
      router.toggleHistory(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.QUICK_SAVE, code)) {
      router.quickSave(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.QUICK_LOAD, code)) {
      router.quickLoad(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.ROLLBACK, code)) {
      router.rollback(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.ROLLFORWARD, code)) {
      router.rollforward(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.MENU_BACK, code)) {
      router.menuBack(engine);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.MENU_UP, code)) {
      router.menuMove(engine, -1);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.MENU_DOWN, code)) {
      router.menuMove(engine, 1);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.MENU_LEFT, code)) {
      router.settingsAdjust(engine, -1);
    }
    if (actionMap.matches(com.jvn.core.input.InputActions.MENU_RIGHT, code)) {
      router.settingsAdjust(engine, 1);
    }
  }
}
