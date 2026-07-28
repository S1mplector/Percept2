package com.jvn.core.tween;

import java.util.ArrayList;
import java.util.List;

public class TweenRunner {
  private final List<TweenTask> tasks = new ArrayList<>();
  /** Tweens added during update() are deferred to the next frame to avoid CME. */
  private final List<TweenTask> pendingAdds = new ArrayList<>();
  private boolean updating = false;

  public void add(TweenTask task) {
    if (task == null) return;
    if (updating) pendingAdds.add(task);
    else tasks.add(task);
  }

  public int activeCount() { return tasks.size(); }

  public void update(long deltaMs) {
    if (updating) {
      throw new IllegalStateException("TweenRunner.update cannot be called recursively");
    }
    long safeDeltaMs = Math.max(0L, deltaMs);
    RuntimeException firstFailure = null;
    int writeIdx = 0;
    updating = true;
    try {
      for (int readIdx = 0; readIdx < tasks.size(); readIdx++) {
        TweenTask t = tasks.get(readIdx);
        boolean keep = false;
        try {
          t.update(safeDeltaMs);
          keep = !t.isFinished();
        } catch (RuntimeException ex) {
          // Remove a poisoned task, but finish compacting the runner so one
          // callback cannot corrupt every tween scheduled after it.
          if (firstFailure == null) firstFailure = ex;
        }
        if (keep) {
          if (writeIdx != readIdx) tasks.set(writeIdx, t);
          writeIdx++;
        }
      }
    } finally {
      // Always restore a valid compact list and publish deferred additions,
      // including when a task throws.
      while (tasks.size() > writeIdx) tasks.remove(tasks.size() - 1);
      updating = false;
      if (!pendingAdds.isEmpty()) {
        tasks.addAll(pendingAdds);
        pendingAdds.clear();
      }
    }
    if (firstFailure != null) throw firstFailure;
  }

  public static abstract class TweenTask {
    public abstract void update(long deltaMs);
    public abstract boolean isFinished();
  }
}
