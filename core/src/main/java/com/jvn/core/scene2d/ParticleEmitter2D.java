package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * A CPU-driven 2D particle emitter that spawns, simulates, and renders
 * lightweight particles with configurable physics, colour, and lifetime.
 *
 * <p>{@code ParticleEmitter2D} supports:</p>
 * <ul>
 *   <li><b>Continuous emission</b> at a configurable rate (particles/sec).</li>
 *   <li><b>Burst emission</b> via {@link #burst(int)} for one-shot effects.</li>
 *   <li><b>Randomised spawn</b> — each particle gets a random speed, angle,
 *       lifetime, size, and rotation from configurable min/max ranges.</li>
 *   <li><b>Per-particle interpolation</b> — size and colour lerp from start
 *       to end values over each particle's lifetime.</li>
 *   <li><b>Gravity</b> — a constant downward (positive Y) acceleration.</li>
 *   <li><b>Additive blending</b> (default) for glowing fire/spark effects.</li>
 *   <li><b>Optional texture</b> — particles can be drawn as filled circles
 *       or as textured quads.</li>
 * </ul>
 *
 * @see Entity2D
 * @see Blitter2D#setBlendMode(String)
 */
public class ParticleEmitter2D extends Entity2D {

  /**
   * Mutable state for a single active particle.
   * Fields are updated in-place each frame for cache-friendly iteration.
   */
  public static class Particle {
    /** Position relative to the emitter's entity position. */
    double x, y;
    /** Velocity in world units per second. */
    double vx, vy;
    /** Elapsed life and maximum lifetime in seconds. */
    double life, maxLife;
    /** Current display size and start/end sizes for interpolation. */
    double size, startSize, endSize;
    /** Current RGBA colour components [0.0, 1.0]. */
    double r, g, b, a;
    /** Current rotation (degrees) and angular velocity (deg/sec). */
    double rotation, rotationSpeed;
  }

  /** Pool of currently alive particles. */
  private final List<Particle> particles = new ArrayList<>();

  /** Random number generator for particle initialisation. */
  private final Random rnd = new Random();

  // ── Emission settings ──────────────────────────────────────────────────

  /** Particles emitted per second during continuous emission. */
  private double emissionRate = 10;

  /** Fractional emission accumulator (carries over between frames). */
  private double emissionAccum = 0;

  /** Maximum number of concurrent alive particles. */
  private int maxParticles = 500;

  /** Whether continuous emission is active. */
  private boolean emitting = true;

  // ── Particle lifetime & size ──────────────────────────────────────────

  /** Minimum particle lifetime in seconds. */
  private double minLife = 1.0;

  /** Maximum particle lifetime in seconds. */
  private double maxLife = 3.0;

  /** Minimum initial particle size (radius or half-width). */
  private double minSize = 2.0;

  /** Maximum initial particle size. */
  private double maxSize = 8.0;

  /** End-of-life size as a fraction of start size (0.1 = shrink to 10%). */
  private double endSizeScale = 0.1;

  // ── Velocity & physics ────────────────────────────────────────────────

  /** Minimum initial speed (world units/sec). */
  private double minSpeed = 50;

  /** Maximum initial speed. */
  private double maxSpeed = 150;

  /** Minimum emission angle in degrees (0 = right). */
  private double minAngle = 0;

  /** Maximum emission angle in degrees. */
  private double maxAngle = 360;

  /** Minimum spawn X offset relative to the emitter origin. */
  private double minSpawnX = 0;

  /** Maximum spawn X offset relative to the emitter origin. */
  private double maxSpawnX = 0;

  /** Minimum spawn Y offset relative to the emitter origin. */
  private double minSpawnY = 0;

  /** Maximum spawn Y offset relative to the emitter origin. */
  private double maxSpawnY = 0;

  /** Vertical gravity acceleration (positive = downward). */
  private double gravityY = 100;

  /**
   * Horizontal acceleration applied every frame, used to model wind or drift.
   * Positive values push particles to the right, negative to the left.
   * Defaults to {@code 0} (still air).
   */
  private double windX = 0;

  // ── Colour ────────────────────────────────────────────────────────────

  /** Start colour RGBA (particles are born with this colour). */
  private double startR = 1, startG = 0.5, startB = 0.2, startA = 1;

  /** End colour RGBA (particles lerp to this colour before dying). */
  private double endR = 1, endG = 0.2, endB = 0.1, endA = 0;

  // ── Visual ────────────────────────────────────────────────────────────

  /** Whether to use additive blend mode (glowing effects). */
  private boolean useAdditive = true;

  /** Optional texture path; {@code null} = draw filled circles. */
  private String texture = null;

  /** Default constructor — creates an emitter with default fire-like settings. */
  public ParticleEmitter2D() {}

  // ──────────────────────────────────────────────────────────────────────────
  //  Configuration setters & getters
  // ──────────────────────────────────────────────────────────────────────────

  /** @param rate particles per second */
  public void setEmissionRate(double rate) { this.emissionRate = rate; }
  /** @return particles per second */
  public double getEmissionRate() { return emissionRate; }
  /** @param max maximum concurrent alive particles */
  public void setMaxParticles(int max) { this.maxParticles = max; }
  /** @return maximum concurrent alive particles */
  public int getMaxParticles() { return maxParticles; }
  /** @param emit whether continuous emission is active */
  public void setEmitting(boolean emit) { this.emitting = emit; }
  /** @return {@code true} when continuous emission is active */
  public boolean isEmitting() { return emitting; }

  /** Set the min/max particle lifetime range (seconds). */
  public void setLifeRange(double min, double max) { this.minLife = min; this.maxLife = max; }
  /** @return minimum lifetime (seconds) */
  public double getMinLife() { return minLife; }
  /** @return maximum lifetime (seconds) */
  public double getMaxLife() { return maxLife; }

  /** Set the min/max initial size and the end-of-life size scale factor. */
  public void setSizeRange(double min, double max, double endScale) { 
    this.minSize = min; this.maxSize = max; this.endSizeScale = endScale; 
  }
  /** @return minimum initial size */
  public double getMinSize() { return minSize; }
  /** @return maximum initial size */
  public double getMaxSize() { return maxSize; }
  /** @return end-of-life size as fraction of start size */
  public double getEndSizeScale() { return endSizeScale; }

  /** Set the min/max initial speed range (world units/sec). */
  public void setSpeedRange(double min, double max) { this.minSpeed = min; this.maxSpeed = max; }
  /** @return minimum initial speed */
  public double getMinSpeed() { return minSpeed; }
  /** @return maximum initial speed */
  public double getMaxSpeed() { return maxSpeed; }

  /** Set the emission angle range in degrees. */
  public void setAngleRange(double min, double max) { this.minAngle = min; this.maxAngle = max; }
  /** @return minimum emission angle (degrees) */
  public double getMinAngle() { return minAngle; }
  /** @return maximum emission angle (degrees) */
  public double getMaxAngle() { return maxAngle; }

  /**
   * Set the rectangular spawn area relative to the emitter origin. The default
   * area is a single point at {@code (0, 0)}, preserving the legacy emitter
   * behaviour.
   */
  public void setSpawnArea(double minX, double maxX, double minY, double maxY) {
    this.minSpawnX = Math.min(minX, maxX);
    this.maxSpawnX = Math.max(minX, maxX);
    this.minSpawnY = Math.min(minY, maxY);
    this.maxSpawnY = Math.max(minY, maxY);
  }
  /** Reset spawning back to the emitter origin. */
  public void clearSpawnArea() { setSpawnArea(0, 0, 0, 0); }
  /** @return minimum spawn X offset */
  public double getMinSpawnX() { return minSpawnX; }
  /** @return maximum spawn X offset */
  public double getMaxSpawnX() { return maxSpawnX; }
  /** @return minimum spawn Y offset */
  public double getMinSpawnY() { return minSpawnY; }
  /** @return maximum spawn Y offset */
  public double getMaxSpawnY() { return maxSpawnY; }

  /** @param gy vertical gravity acceleration (positive = downward) */
  public void setGravity(double gy) { this.gravityY = gy; }
  /** @return vertical gravity */
  public double getGravityY() { return gravityY; }

  /**
   * Set horizontal wind acceleration (world units / sec²). Positive values push
   * particles to the right; negative push to the left. Useful for snow drift,
   * slanted rain, and similar weather effects.
   *
   * @param wx horizontal acceleration
   */
  public void setWindX(double wx) { this.windX = wx; }
  /** @return horizontal wind acceleration (world units / sec²) */
  public double getWindX() { return windX; }

  /** Set the start colour (RGBA, [0.0, 1.0]). */
  public void setStartColor(double r, double g, double b, double a) {
    this.startR = r; this.startG = g; this.startB = b; this.startA = a;
  }
  /** @return start red */
  public double getStartR() { return startR; }
  /** @return start green */
  public double getStartG() { return startG; }
  /** @return start blue */
  public double getStartB() { return startB; }
  /** @return start alpha */
  public double getStartA() { return startA; }

  /** Set the end colour (RGBA, [0.0, 1.0]). */
  public void setEndColor(double r, double g, double b, double a) {
    this.endR = r; this.endG = g; this.endB = b; this.endA = a;
  }
  /** @return end red */
  public double getEndR() { return endR; }
  /** @return end green */
  public double getEndG() { return endG; }
  /** @return end blue */
  public double getEndB() { return endB; }
  /** @return end alpha */
  public double getEndA() { return endA; }

  /** @param path texture asset path, or {@code null} for filled circles */
  public void setTexture(String path) { this.texture = path; }
  /** @return texture path, or {@code null} */
  public String getTexture() { return texture; }
  /** @param add whether to use additive blend mode */
  public void setAdditive(boolean add) { this.useAdditive = add; }
  /** @return {@code true} if additive blending is active */
  public boolean isAdditive() { return useAdditive; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Emission
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Instantly emit a burst of particles (up to the max particle cap).
   *
   * @param count number of particles to spawn
   */
  public void burst(int count) {
    for (int i = 0; i < count && particles.size() < maxParticles; i++) {
      emit();
    }
  }

  /** Spawn a single particle with randomised properties from the configured ranges. */
  private void emit() {
    Particle p = new Particle();
    p.x = randomRange(minSpawnX, maxSpawnX);
    p.y = randomRange(minSpawnY, maxSpawnY);
    
    double angle = Math.toRadians(minAngle + rnd.nextDouble() * (maxAngle - minAngle));
    double speed = minSpeed + rnd.nextDouble() * (maxSpeed - minSpeed);
    p.vx = Math.cos(angle) * speed;
    p.vy = Math.sin(angle) * speed;
    
    p.maxLife = minLife + rnd.nextDouble() * (maxLife - minLife);
    p.life = 0;
    
    p.startSize = minSize + rnd.nextDouble() * (maxSize - minSize);
    p.endSize = p.startSize * endSizeScale;
    p.size = p.startSize;
    
    p.r = startR;
    p.g = startG;
    p.b = startB;
    p.a = startA;
    
    p.rotation = rnd.nextDouble() * 360;
    p.rotationSpeed = (rnd.nextDouble() - 0.5) * 360;
    
    particles.add(p);
  }
  
  /**
   * Advance the particle simulation: emit new particles (if active),
   * apply physics and gravity, interpolate colour/size, and remove
   * expired particles.
   */
  @Override
  public void update(long deltaMs) {
    double dt = deltaMs / 1000.0;
    
    // Continuous emission: accumulate fractional particles and spawn whole ones
    if (emitting) {
      emissionAccum += emissionRate * dt;
      while (emissionAccum >= 1.0 && particles.size() < maxParticles) {
        emit();
        emissionAccum -= 1.0;
      }
    }
    
    // Update particles
    Iterator<Particle> it = particles.iterator();
    while (it.hasNext()) {
      Particle p = it.next();
      
      p.life += dt;
      if (p.life >= p.maxLife) {
        it.remove();
        continue;
      }
      
      // Physics
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.vx += windX * dt;
      p.vy += gravityY * dt;
      p.rotation += p.rotationSpeed * dt;
      
      // Interpolation
      double t = p.life / p.maxLife;
      p.size = p.startSize + (p.endSize - p.startSize) * t;
      p.r = startR + (endR - startR) * t;
      p.g = startG + (endG - startG) * t;
      p.b = startB + (endB - startB) * t;
      p.a = startA + (endA - startA) * t;
    }
  }
  
  /**
   * Render all alive particles. Each particle is drawn as either a textured
   * quad or a filled circle, with per-particle alpha, rotation, and colour.
   * Additive blending is enabled/disabled around the batch.
   */
  @Override
  public void render(Blitter2D b) {
    if (particles.isEmpty()) return;
    
    b.push();
    if (useAdditive) b.setBlendMode("additive");
    
    for (Particle p : particles) {
      b.push();
      b.translate(p.x, p.y);
      b.rotateDeg(p.rotation);
      b.setGlobalAlpha(p.a);
      
      if (texture != null) {
        double hs = p.size / 2;
        b.drawImage(texture, -hs, -hs, p.size, p.size);
      } else {
        b.setFill(p.r, p.g, p.b, p.a);
        b.fillCircle(0, 0, p.size / 2);
      }
      
      b.pop();
    }
    
    if (useAdditive) b.setBlendMode("normal");
    b.pop();
  }
  
  /** @return the number of currently alive particles */
  public int getParticleCount() { return particles.size(); }

  /** Remove all alive particles immediately. */
  public void clear() { particles.clear(); }

  private double randomRange(double min, double max) {
    if (Math.abs(max - min) < 1e-9) return min;
    return min + rnd.nextDouble() * (max - min);
  }
}
