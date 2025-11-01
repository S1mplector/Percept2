package com.jvn.core.tween;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TweenRunner {
  private final List<TweenTask> tasks = new ArrayList<>();

  public void add(TweenTask task) { if (task != null) tasks.add(task); }

  public void update(long deltaMs) {
    Iterator<TweenTask> it = tasks.iterator();
    while (it.hasNext()) {
      TweenTask t = it.next();
      t.update(deltaMs);
      if (t.isFinished()) it.remove();
    }
  }

  public static abstract class TweenTask {
    public abstract void update(long deltaMs);
    public abstract boolean isFinished();
  }
}
