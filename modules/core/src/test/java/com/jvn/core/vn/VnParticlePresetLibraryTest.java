package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.scene2d.ParticleEmitter2D;

public class VnParticlePresetLibraryTest {

  @Test
  public void snowPresetUsesSceneSizedSpawnAreaAndCommandOverrides() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticleCommand command = VnParticleCommand.builder(VnParticleCommand.Preset.SNOW)
        .intensity(0.6f)
        .layer(90)
        .opacity(0.5)
        .speed(1.25)
        .wind(-18.0)
        .size(1.5)
        .prewarm(3000)
        .texture("assets/vfx/snowflake.png")
        .tint(0x0088AAFF)
        .build();

    VnParticlePresetLibrary.apply(emitter, command, 1600, 900);

    assertTrue(emitter.isEmitting());
    assertEquals(90.0, emitter.getZ(), 0.0001);
    assertEquals(800.0, emitter.getX(), 0.0001);
    assertEquals(-54.0, emitter.getY(), 0.0001);
    assertEquals(-18.0, emitter.getWindX(), 0.0001);
    assertEquals(25.0, emitter.getMinSpeed(), 0.0001);
    assertEquals(68.75, emitter.getMaxSpeed(), 0.0001);
    assertEquals(3.75, emitter.getMinSize(), 0.0001);
    assertEquals(9.0, emitter.getMaxSize(), 0.0001);
    assertEquals("assets/vfx/snowflake.png", emitter.getTexture());
    assertEquals(3000L, command.getPrewarmMs());
    assertEquals(-928.0, emitter.getMinSpawnX(), 0.0001);
    assertEquals(928.0, emitter.getMaxSpawnX(), 0.0001);
    assertEquals(0x88 / 255.0, emitter.getStartR(), 0.0001);
    assertEquals(0xAA / 255.0, emitter.getStartG(), 0.0001);
    assertEquals(0xFF / 255.0, emitter.getStartB(), 0.0001);
    assertEquals(0.475, emitter.getStartA(), 0.0001);
  }

  @Test
  public void stopPresetDisablesEmissionWithoutClearingLiveParticles() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    emitter.burst(5);

    VnParticlePresetLibrary.apply(emitter, VnParticleCommand.stop(), 1280, 720);

    assertFalse(emitter.isEmitting());
    assertEquals(0.0, emitter.getEmissionRate(), 0.0001);
    assertEquals(5, emitter.getParticleCount());
  }

  @Test
  public void rainPresetUsesFastStreakRenderer() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticleCommand command = VnParticleCommand.builder(VnParticleCommand.Preset.RAIN)
        .intensity(0.75f)
        .speed(1.2)
        .wind(24.0)
        .build();

    VnParticlePresetLibrary.apply(emitter, command, 1600, 900);

    assertTrue(emitter.isEmitting());
    assertFalse(emitter.isAdditive());
    assertEquals(ParticleEmitter2D.RenderMode.STREAK, emitter.getRenderMode());
    assertEquals(0.060, emitter.getStreakLengthScale(), 0.0001);
    assertEquals(1140.0, emitter.getMinSpeed(), 0.0001);
    assertEquals(1440.0, emitter.getMaxSpeed(), 0.0001);
    assertEquals(84.0, emitter.getMinAngle(), 0.0001);
    assertEquals(96.0, emitter.getMaxAngle(), 0.0001);
    assertEquals(24.0, emitter.getWindX(), 0.0001);
    assertEquals(0.62, emitter.getStartA(), 0.0001);
    assertEquals(220.0, emitter.getGravityY(), 0.0001);
    assertEquals(1.2, emitter.getMinSize(), 0.0001);
    assertEquals(2.0, emitter.getMaxSize(), 0.0001);
  }

  @Test
  public void sakuraPresetUsesPetalLikeDriftAndTintableColor() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticleCommand command = VnParticleCommand.builder(VnParticleCommand.Preset.SAKURA)
        .intensity(0.5f)
        .layer(80)
        .wind(12.0)
        .opacity(0.75)
        .build();

    VnParticlePresetLibrary.apply(emitter, command, 1280, 720);

    assertTrue(emitter.isEmitting());
    assertEquals(80.0, emitter.getZ(), 0.0001);
    assertEquals(640.0, emitter.getX(), 0.0001);
    assertEquals(-57.6, emitter.getY(), 0.0001);
    assertEquals(-742.4, emitter.getMinSpawnX(), 0.0001);
    assertEquals(742.4, emitter.getMaxSpawnX(), 0.0001);
    assertEquals(68.0, emitter.getMinAngle(), 0.0001);
    assertEquals(118.0, emitter.getMaxAngle(), 0.0001);
    assertEquals(12.0, emitter.getWindX(), 0.0001);
    assertFalse(emitter.isAdditive());
    assertEquals(18.0, emitter.getMinSize(), 0.0001);
    assertEquals(34.0, emitter.getMaxSize(), 0.0001);
    // start alpha 1.0 × opacity 0.75 = 0.75
    assertEquals(0.75, emitter.getStartA(), 0.0001);
    // Texture pool: nine bundled petal sprites; emitter exposes the legacy
    // single-texture field as the first entry for backward compatibility.
    assertEquals(9, emitter.getTextures().size());
    assertEquals("com/jvn/fx/particles/sakura/petal1.png", emitter.getTexture());
    assertEquals(VnParticlePresetLibrary.SAKURA_PETAL_TEXTURES, emitter.getTextures());
  }

  @Test
  public void setTextureClearsTexturePool() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    emitter.setTextures(VnParticlePresetLibrary.SAKURA_PETAL_TEXTURES);
    assertEquals(9, emitter.getTextures().size());

    emitter.setTexture("custom/single.png");
    assertEquals("custom/single.png", emitter.getTexture());
    assertTrue(emitter.getTextures().isEmpty());
  }

  @Test
  public void firefliesPresetIsSparseAdditiveAndRises() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticleCommand command = VnParticleCommand.builder(VnParticleCommand.Preset.FIREFLIES)
        .intensity(1.0f)
        .speed(2.0)
        .build();

    VnParticlePresetLibrary.apply(emitter, command, 1000, 500);

    assertTrue(emitter.isEmitting());
    assertTrue(emitter.isAdditive());
    assertEquals(8.0, emitter.getEmissionRate(), 0.0001);
    assertEquals(16.0, emitter.getMinSpeed(), 0.0001);
    assertEquals(52.0, emitter.getMaxSpeed(), 0.0001);
    assertEquals(-10.0, emitter.getGravityY(), 0.0001);
    assertEquals(210.0, emitter.getMinAngle(), 0.0001);
    assertEquals(330.0, emitter.getMaxAngle(), 0.0001);
  }

  @Test
  public void dustPresetFillsSceneWithLowAlphaMotes() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticleCommand command = VnParticleCommand.builder(VnParticleCommand.Preset.DUST)
        .intensity(0.0f)
        .build();

    VnParticlePresetLibrary.apply(emitter, command, 1200, 800);

    assertTrue(emitter.isEmitting());
    assertTrue(emitter.isAdditive());
    assertEquals(600.0, emitter.getX(), 0.0001);
    assertEquals(400.0, emitter.getY(), 0.0001);
    assertEquals(-624.0, emitter.getMinSpawnX(), 0.0001);
    assertEquals(624.0, emitter.getMaxSpawnX(), 0.0001);
    assertEquals(-416.0, emitter.getMinSpawnY(), 0.0001);
    assertEquals(416.0, emitter.getMaxSpawnY(), 0.0001);
    assertEquals(1.8, emitter.getEmissionRate(), 0.0001);
    assertEquals(0.22, emitter.getStartA(), 0.0001);
  }

  @Test
  public void leavesPresetUsesLargerForegroundParticles() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    VnParticleCommand command = VnParticleCommand.builder(VnParticleCommand.Preset.LEAVES)
        .intensity(0.8f)
        .wind(-20.0)
        .tint(0xCCDD7722)
        .build();

    VnParticlePresetLibrary.apply(emitter, command, 1400, 800);

    assertTrue(emitter.isEmitting());
    assertFalse(emitter.isAdditive());
    assertEquals(7.0, emitter.getMinSize(), 0.0001);
    assertEquals(15.0, emitter.getMaxSize(), 0.0001);
    assertEquals(-20.0, emitter.getWindX(), 0.0001);
    assertEquals(54.0, emitter.getGravityY(), 0.0001);
    assertEquals(0xDD / 255.0, emitter.getStartR(), 0.0001);
    assertEquals(0x77 / 255.0, emitter.getStartG(), 0.0001);
    assertEquals(0x22 / 255.0, emitter.getStartB(), 0.0001);
    assertEquals(0.90 * (0xCC / 255.0), emitter.getStartA(), 0.0001);
  }

  @Test
  public void stateExpiresTimedParticleCommand() {
    VnState state = new VnState();
    state.setActiveParticleCommand(VnParticleCommand.builder(VnParticleCommand.Preset.RAIN)
        .duration(100)
        .build());

    state.updateParticleEffect(40);
    assertNotNull(state.getActiveParticleCommand());
    assertEquals(60L, state.getActiveParticleRemainingMs());

    state.updateParticleEffect(60);
    assertNull(state.getActiveParticleCommand());
    assertEquals(0L, state.getActiveParticleRemainingMs());
  }
}
