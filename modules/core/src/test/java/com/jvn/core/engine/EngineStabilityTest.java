package com.jvn.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jvn.core.scene.Scene;

/**
 * Stability regression tests for {@link Engine}.
 *
 * <p>These tests encode invariants that are easy to silently break:
 * listener re-entrancy, exception containment, and input-frame
 * cleanup guarantees. Prior to the stability pass that introduced
 * these tests, each of the following scenarios could corrupt runtime
 * state invisibly.</p>
 */
public class EngineStabilityTest {

  private PrintStream originalErr;
  private ByteArrayOutputStream capturedErr;

  @BeforeEach
  public void captureStderr() {
    // Several tests deliberately trigger exceptions that the Engine logs to
    // System.err. Capture the stream so unit output stays clean.
    originalErr = System.err;
    capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));
  }

  @AfterEach
  public void restoreStderr() {
    System.setErr(originalErr);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Listener re-entrancy
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  public void listenerCanRemoveItselfFromInsidePreUpdate() {
    Engine engine = new Engine(null);
    engine.start();

    List<String> calls = new ArrayList<>();
    EngineListener selfRemoving = new EngineListener() {
      @Override public void preUpdate(long dt) {
        calls.add("selfRemove.pre");
        engine.removeListener(this);
      }
    };
    EngineListener neighbour = new EngineListener() {
      @Override public void preUpdate(long dt) { calls.add("neighbour.pre"); }
      @Override public void postUpdate(long dt) { calls.add("neighbour.post"); }
    };

    engine.addListener(selfRemoving);
    engine.addListener(neighbour);

    // Prior to the re-entrancy fix this would either skip `neighbour.pre`
    // (because the list shrank mid-iteration) or throw an IOOBE.
    engine.update(16);

    assertTrue(calls.contains("selfRemove.pre"));
    assertTrue(calls.contains("neighbour.pre"),
        "neighbour listener must still be dispatched when its predecessor self-removes");
    assertTrue(calls.contains("neighbour.post"));

    // Second frame: self-removing listener should be gone now.
    calls.clear();
    engine.update(16);
    assertFalse(calls.contains("selfRemove.pre"),
        "self-removing listener should have been unregistered after dispatch finished");
    assertTrue(calls.contains("neighbour.pre"));
  }

  @Test
  public void listenerCanAddNewListenerFromInsidePostUpdate() {
    Engine engine = new Engine(null);
    engine.start();

    List<String> calls = new ArrayList<>();
    EngineListener late = new EngineListener() {
      @Override public void postUpdate(long dt) { calls.add("late.post"); }
    };
    EngineListener adder = new EngineListener() {
      @Override public void postUpdate(long dt) {
        calls.add("adder.post");
        engine.addListener(late);
      }
    };

    engine.addListener(adder);
    engine.update(16); // adder fires, queues `late` for next frame.

    assertTrue(calls.contains("adder.post"));
    assertFalse(calls.contains("late.post"),
        "newly added listener should not be fired until the next dispatch");

    calls.clear();
    engine.update(16);
    assertTrue(calls.contains("late.post"),
        "listener added during dispatch should be active on the following frame");
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Exception safety
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  public void throwingSceneUpdateStillEndsInputFrameAndRunsPostListeners() {
    Engine engine = new Engine(null);
    engine.start();

    engine.input().keyDown("SPACE");
    assertTrue(engine.input().wasKeyPressed("SPACE"),
        "precondition: edge-triggered pressed set should contain SPACE");

    List<String> calls = new ArrayList<>();
    engine.addListener(new EngineListener() {
      @Override public void postUpdate(long dt) { calls.add("post"); }
    });
    engine.scenes().push(new Scene() {
      @Override public void update(long deltaMs) {
        throw new RuntimeException("intentional scene update failure");
      }
    });

    // Even though the scene throws, the finally block must still run
    // input.endFrame() and the postUpdate dispatch.
    try {
      engine.update(16);
    } catch (RuntimeException ignored) {
      // The engine is free to either swallow the scene's exception or
      // rethrow it; the important invariants below must hold either way.
    }

    assertFalse(engine.input().wasKeyPressed("SPACE"),
        "input.endFrame() must run even when the scene's update() throws");
    assertEquals(1, calls.size(), "postUpdate listeners must still fire after a failing scene");
  }

  @Test
  public void throwingListenerDoesNotPreventOtherListenersFromBeingDispatched() {
    Engine engine = new Engine(null);
    engine.start();

    List<String> calls = new ArrayList<>();
    engine.addListener(new EngineListener() {
      @Override public void preUpdate(long dt) {
        throw new RuntimeException("intentional listener failure");
      }
    });
    engine.addListener(new EngineListener() {
      @Override public void preUpdate(long dt) { calls.add("survivor.pre"); }
    });

    engine.update(16);

    assertTrue(calls.contains("survivor.pre"),
        "a listener that throws must not suppress subsequent listeners' callbacks");
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Interpolation alpha bounds
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  public void interpolationAlphaStaysWithinUnitRangeUnderSpiralOfDeath() {
    Engine engine = new Engine(null);
    engine.setFixedUpdateStepMs(16, 4); // 62.5Hz physics, max 4 steps
    engine.start();

    // Huge frame delta forces the spiral-of-death branch: accumulator can't
    // drain in a single frame.
    engine.setMaxDeltaMs(0); // disable clamping for this test
    engine.update(10_000);

    double alpha = engine.getInterpolationAlpha();
    assertTrue(alpha >= 0.0 && alpha <= 1.0,
        "interpolationAlpha must stay within [0,1]; got " + alpha);
  }
}
