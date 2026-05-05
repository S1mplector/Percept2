package com.jvn.core.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link SceneManager} keeps its stack invariants intact even when
 * scene lifecycle methods ({@code onEnter}, {@code onPause}, {@code onExit},
 * {@code onResume}) throw {@link RuntimeException}.
 *
 * <p>A misbehaving scene previously could:</p>
 * <ul>
 *   <li>Leave the underlying scene paused forever (throwing {@code onPause}
 *       followed by a failed push means the engine keeps driving a scene
 *       that thinks it's paused).</li>
 *   <li>Skip the {@code onResume} callback on the scene below after a
 *       failing {@code onExit}, starving it of audio/animation wake-up.</li>
 *   <li>Silently empty the stack when {@code replace(null)} was called by a
 *       caller that forgot to null-check.</li>
 * </ul>
 */
public class SceneManagerStabilityTest {

  private PrintStream originalErr;
  private ByteArrayOutputStream capturedErr;

  @BeforeEach
  public void captureStderr() {
    originalErr = System.err;
    capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));
  }

  @AfterEach
  public void restoreStderr() {
    System.setErr(originalErr);
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private static final class RecordingScene implements Scene {
    final String name;
    final List<String> log;
    boolean throwOnEnter;
    boolean throwOnPause;
    boolean throwOnExit;
    boolean throwOnResume;

    RecordingScene(String name, List<String> log) {
      this.name = name;
      this.log = log;
    }

    @Override public void onEnter() {
      log.add(name + ".onEnter");
      if (throwOnEnter) throw new RuntimeException("boom onEnter " + name);
    }
    @Override public void onPause() {
      log.add(name + ".onPause");
      if (throwOnPause) throw new RuntimeException("boom onPause " + name);
    }
    @Override public void onExit() {
      log.add(name + ".onExit");
      if (throwOnExit) throw new RuntimeException("boom onExit " + name);
    }
    @Override public void onResume() {
      log.add(name + ".onResume");
      if (throwOnResume) throw new RuntimeException("boom onResume " + name);
    }
    @Override public void update(long deltaMs) {}
  }

  // ── tests ──────────────────────────────────────────────────────────────

  @Test
  public void pushThatThrowsOnPausePreviousSceneStillSeesNewSceneOnTop() {
    List<String> log = new ArrayList<>();
    SceneManager mgr = new SceneManager();

    RecordingScene first = new RecordingScene("first", log);
    RecordingScene second = new RecordingScene("second", log);
    first.throwOnPause = true;

    mgr.push(first);
    mgr.push(second);

    assertSame(second, mgr.peek(),
        "the new scene must be on top even if the previous scene's onPause threw");
    assertTrue(log.contains("first.onPause"));
    assertTrue(log.contains("second.onEnter"),
        "onEnter for the new scene must still fire after a failing onPause");
  }

  @Test
  public void popStillResumesBelowSceneEvenIfOnExitThrew() {
    List<String> log = new ArrayList<>();
    SceneManager mgr = new SceneManager();

    RecordingScene below = new RecordingScene("below", log);
    RecordingScene above = new RecordingScene("above", log);
    above.throwOnExit = true;

    mgr.push(below);
    mgr.push(above);
    log.clear();

    Scene popped = mgr.pop();

    assertSame(above, popped);
    assertSame(below, mgr.peek());
    assertTrue(log.contains("above.onExit"));
    assertTrue(log.contains("below.onResume"),
        "below-scene.onResume must run even when the above-scene's onExit threw");
  }

  @Test
  public void replaceNullDegradesToPopRatherThanSilentlyEmptyingTheStack() {
    List<String> log = new ArrayList<>();
    SceneManager mgr = new SceneManager();

    RecordingScene below = new RecordingScene("below", log);
    RecordingScene above = new RecordingScene("above", log);
    mgr.push(below);
    mgr.push(above);
    log.clear();

    mgr.replace(null);

    assertSame(below, mgr.peek(),
        "replace(null) must not leave the stack empty — it should behave like pop()");
    assertTrue(log.contains("above.onExit"));
    assertTrue(log.contains("below.onResume"));
  }

  @Test
  public void replaceKeepsStackIntactWhenReplacedSceneOnExitThrows() {
    List<String> log = new ArrayList<>();
    SceneManager mgr = new SceneManager();

    RecordingScene oldTop = new RecordingScene("old", log);
    RecordingScene newTop = new RecordingScene("new", log);
    oldTop.throwOnExit = true;
    mgr.push(oldTop);
    log.clear();

    mgr.replace(newTop);

    assertSame(newTop, mgr.peek(),
        "replacement scene must be on top even if the outgoing scene's onExit threw");
    assertEquals(1, countInStack(mgr), "stack depth must be exactly 1 after replace()");
    assertTrue(log.contains("old.onExit"));
    assertTrue(log.contains("new.onEnter"));
  }

  @Test
  public void popOnEmptyStackIsANoOpAndReturnsNull() {
    SceneManager mgr = new SceneManager();
    assertNull(mgr.pop());
    assertTrue(mgr.isEmpty());
  }

  // Reflection-free depth count helper: pop everything into a buffer and
  // restore. Used only in assertions.
  private static int countInStack(SceneManager mgr) {
    int depth = 0;
    List<Scene> buf = new ArrayList<>();
    while (!mgr.isEmpty()) {
      buf.add(mgr.pop());
      depth++;
    }
    // Restore in original order (buf[0] was the top).
    for (int i = buf.size() - 1; i >= 0; i--) {
      mgr.push(buf.get(i));
    }
    return depth;
  }
}
