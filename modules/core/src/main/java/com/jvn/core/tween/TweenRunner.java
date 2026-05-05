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
    // Index-based loop tolerates the underlying list being mutated below
    // (we never let add() touch `tasks` during update).
    updating = true;
    try {
      int writeIdx = 0;
      for (int readIdx = 0; readIdx < tasks.size(); readIdx++) {
        TweenTask t = tasks.get(readIdx);
        t.update(deltaMs);
        if (!t.isFinished()) {
          if (writeIdx != readIdx) tasks.set(writeIdx, t);
          writeIdx++;
        }
      }
      // Trim any tail that's now beyond the kept tasks (in O(removed) not O(n*removed)).
      while (tasks.size() > writeIdx) tasks.remove(tasks.size() - 1);
    } finally {
      updating = false;
    }
    if (!pendingAdds.isEmpty()) {
      tasks.addAll(pendingAdds);
      pendingAdds.clear();
    }
  }

  public static abstract class TweenTask {
    public abstract void update(long deltaMs);
    public abstract boolean isFinished();
  }
}
