package com.jvn.fx.particles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;

import org.junit.jupiter.api.Test;

import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.vn.VnParticleCommand;
import com.jvn.core.vn.VnParticlePresetLibrary;

/**
 * Regression tests for the bundled sakura petal sprites. These tests don't
 * boot JavaFX; they only verify that:
 *
 * <ol>
 *   <li>The PNG files declared by {@link VnParticlePresetLibrary#SAKURA_PETAL_TEXTURES}
 *       are physically present on the {@code fx} module's runtime classpath.</li>
 *   <li>The SAKURA preset wires the emitter's texture pool to those paths,
 *       so freshly-emitted particles will pick a sprite at spawn instead of
 *       falling back to the legacy pink-circle rendering.</li>
 * </ol>
 *
 * <p>If a future refactor accidentally moves the assets, drops the resource
 * directory from the build, or short-circuits the texture pool wiring, one
 * of these tests will fail loudly instead of silently regressing the live
 * editor preview to "pink blobs".</p>
 */
public class SakuraPetalAssetsTest {

  @Test
  public void allNinePetalSpritesResolveOnTheFxClasspath() {
    ClassLoader cl = SakuraPetalAssetsTest.class.getClassLoader();
    for (String path : VnParticlePresetLibrary.SAKURA_PETAL_TEXTURES) {
      URL url = cl.getResource(path);
      assertNotNull(url, "Missing classpath resource: " + path
          + " — confirm fx/src/main/resources/" + path + " exists.");
    }
  }

  @Test
  public void sakuraPresetBindsEmitterToPetalTexturePool() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticlePresetLibrary.apply(
        emitter,
        VnParticleCommand.builder(VnParticleCommand.Preset.SAKURA).build(),
        1280,
        720);

    assertFalse(emitter.getTextures().isEmpty(),
        "SAKURA preset must populate the texture pool with petal sprites.");
    assertTrue(emitter.getTextures().size() == VnParticlePresetLibrary.SAKURA_PETAL_TEXTURES.size(),
        "Expected " + VnParticlePresetLibrary.SAKURA_PETAL_TEXTURES.size()
            + " petal textures in the pool, got " + emitter.getTextures().size());
  }
}
