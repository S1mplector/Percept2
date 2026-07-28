package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParticleEmitter2DStabilityTest {

  @Test
  void hugeDeltasAndRatesRemainBoundedByParticleCap() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    emitter.setMaxParticles(12);
    emitter.setEmissionRate(Double.MAX_VALUE);

    emitter.update(Long.MAX_VALUE);

    assertEquals(12, emitter.getParticleCount());
  }

  @Test
  void invalidRangesAreNormalizedAndClearReusesStableState() {
    ParticleEmitter2D emitter = new ParticleEmitter2D();
    emitter.setLifeRange(Double.NaN, -5);
    emitter.setSizeRange(10, -2, Double.NaN);
    emitter.setMaxParticles(-1);

    assertTrue(emitter.getMinLife() > 0);
    assertTrue(emitter.getMaxLife() >= emitter.getMinLife());
    assertTrue(emitter.getMinSize() >= 0);
    assertTrue(emitter.getMaxSize() >= emitter.getMinSize());
    assertEquals(0, emitter.getMaxParticles());
    emitter.clear();
    assertEquals(0, emitter.getParticleCount());
  }
}
