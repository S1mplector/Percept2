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
