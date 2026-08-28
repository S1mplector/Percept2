package com.jvn.fx;

import com.jvn.core.engine.Engine;
import com.jvn.core.vn.VnScene;
import com.jvn.scenerender.input.DefaultMenuSceneFactory;

/**
 * Desktop {@link com.jvn.scenerender.input.MenuSceneFactory}. Extends
 * {@link DefaultMenuSceneFactory} (real scene construction, unchanged) and
 * additionally overrides {@link #afterSaveSlotWritten} to restore the
 * save-slot thumbnail write that {@code FxLauncher}'s keyboard-Enter path
 * (its local {@code handleMenuEnter()}) has always performed, but that the
 * shared {@code SceneInputRouter.menuEnter} deliberately omits (JavaFX
 * {@code Canvas.snapshot()}-based, no browser equivalent — see
 * {@code MenuSceneFactory.afterSaveSlotWritten}'s javadoc).
 *
 * <p>Before this class existed, {@code FxLauncher}'s mouse-click path
 * (refactored to delegate to {@code SceneInputRouter.handleClick}, which
 * calls {@code menuEnter} for {@code SaveMenuScene} item-selection clicks)
 * silently regressed: saving via mouse click produced no thumbnail, while
 * saving via the Enter key still did. Wiring {@code FxLauncher} through this
 * factory instead of the plain {@code DefaultMenuSceneFactory} makes both
 * input paths behaviorally identical again.</p>
 *
 * <p>The actual thumbnail-capture logic ({@code Canvas.snapshot()} +
 * {@code ImageIO.write}) needs a live JavaFX {@code Canvas}/{@code VnRenderer}
 * reference that this class — living in the shared {@code scene-render}-adjacent
 * seam pattern — does not hold itself. Rather than duplicating that logic here
 * (which would need its own {@code Canvas}/{@code VnRenderer} fields and drift
 * from {@code FxLauncher}'s existing {@code writeSaveThumbnail}/
 * {@code captureVnThumbnail} methods), this class accepts a small callback
 * supplied by {@code FxLauncher} at construction time and simply forwards to
 * it — keeping exactly one implementation of the capture logic.</p>
 */
public final class FxMenuSceneFactory extends DefaultMenuSceneFactory {

  /** Callback invoked with the just-written save slot's name. */
  @FunctionalInterface
  public interface SaveThumbnailWriter {
    void writeSaveThumbnail(VnScene vnScene, String slotName);
  }

  private final SaveThumbnailWriter thumbnailWriter;

  public FxMenuSceneFactory(SaveThumbnailWriter thumbnailWriter) {
    this.thumbnailWriter = thumbnailWriter;
  }

  @Override
  public void afterSaveSlotWritten(Engine engine, VnScene vnScene, String slotName) {
    if (thumbnailWriter != null) {
      thumbnailWriter.writeSaveThumbnail(vnScene, slotName);
    }
  }
}
