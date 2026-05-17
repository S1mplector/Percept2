# Core Physics API

**Module:** `modules/core`  
**Package:** `com.jvn.core.physics`  
**Purpose:** 2D rigid body physics simulation: collision detection, raycasting, dynamics

---

## Overview

The core physics system provides a high-performance 2D physics engine for JES gameplay and interactive scenes. It supports:
- **Rigid bodies** — objects with mass, velocity, friction
- **Shapes** — axis-aligned boxes (AABB) and circles
- **Collisions** — discrete and continuous collision detection
- **Raycasting** — line-of-sight, hitscan weapons
- **Constraints** — optional joint/constraint system
- **Broad-phase filtering** — collision groups and masks for performance

Used by JES's `PhysicsBody2D` component, but decoupled so non-JES scenes can also use physics.

---

## Core Classes

### PhysicsWorld2D

**Location:** `modules/core/src/main/java/com/jvn/core/physics/PhysicsWorld2D.java`

Container for all physics objects and simulation.

```java
public interface PhysicsWorld2D {
  // Configuration
  void setGravity(double x, double y);         // e.g., (0, 9.8) for earth gravity
  void setTimeStep(double seconds);            // default 1/60s
  void setIterations(int positionIterations, int velocityIterations);
  
  // Object management
  RigidBody2D createBody(BodyDef def);         // create rigid body
  void destroyBody(RigidBody2D body);
  
  // Simulation
  void update(double deltaSeconds);            // step physics forward
  
  // Queries
  List<RigidBody2D> getBodies();
  RigidBody2D getBodyAt(double x, double y);   // point query
  List<RaycastHit> raycast(Ray ray, int mask); // line query
  
  // Listeners
  void addCollisionListener(CollisionListener listener);
  void removeCollisionListener(CollisionListener listener);
}
```

**Usage:**

```java
PhysicsWorld2D world = new Box2DWorld();
world.setGravity(0, 9.8);  // m/s^2

// Step simulation
world.update(deltaSeconds);

// Query results
List<RaycastHit> hits = world.raycast(
  new Ray(fromX, fromY, toX, toY), 
  CollisionGroup.ENEMIES
);
```

---

### RigidBody2D

**Location:** `modules/core/src/main/java/com/jvn/core/physics/RigidBody2D.java`

Individual object in the physics world.

```java
public interface RigidBody2D {
  // Transform
  double getX();
  double getY();
  void setPosition(double x, double y);
  double getRotation();  // radians
  void setRotation(double radians);
  
  // Velocity
  double getVelocityX();
  double getVelocityY();
  void setVelocity(double vx, double vy);
  double getAngularVelocity();  // rad/s
  void setAngularVelocity(double w);
  
  // Forces
  void applyForce(double fx, double fy);
  void applyImpulse(double jx, double jy);
  void applyAngularImpulse(double j);
  
  // Properties
  double getMass();
  void setMass(double mass);
  double getFriction();
  void setFriction(double friction);
  double getRestitution();  // bounce
  void setRestitution(double e);
  
  // Shapes
  void setShape(Shape shape);
  Shape getShape();
  
  // Type
  BodyType getType();  // STATIC, DYNAMIC, KINEMATIC
  void setType(BodyType type);
  
  // State
  void setActive(boolean active);
  boolean isActive();
}
```

**Body Types:**

| Type | Usage | Velocity |
|------|-------|----------|
| **STATIC** | Walls, terrain, platforms | None; moved only by code |
| **DYNAMIC** | Player, enemies, objects | Affected by gravity and forces |
| **KINEMATIC** | Platforms, NPCs | Moved by code, can collide |

**Example:**

```java
// Dynamic body (affected by gravity)
BodyDef def = new BodyDef(BodyType.DYNAMIC);
def.position(100, 100);
def.linearVelocity(5, 0);  // moving right
RigidBody2D hero = world.createBody(def);
hero.setMass(10);
hero.setFriction(0.2);
hero.setRestitution(0.1);  // low bounce

// Apply forces
hero.applyForce(5, 0);  // push right
hero.applyImpulse(0, 20);  // jump
```

---

### Shape

**Location:** `modules/core/src/main/java/com/jvn/core/physics/` (hierarchy)

Collision geometry.

```java
// Axis-aligned box
public class AabbShape implements Shape {
  public AabbShape(double halfWidth, double halfHeight);
  
  public double getHalfWidth();
  public double getHalfHeight();
  public double getWidth();
  public double getHeight();
}

// Circle
public class CircleShape implements Shape {
  public CircleShape(double radius);
  
  public double getRadius();
}

// Polygon (future)
public class PolygonShape implements Shape {
  // vertex-based arbitrary shapes
}
```

**Setting shapes:**

```java
hero.setShape(new CircleShape(16));  // circular collision
ground.setShape(new AabbShape(500, 10));  // rectangular platform
```

---

### CollisionListener

**Location:** `modules/core/src/main/java/com/jvn/core/physics/CollisionListener.java`

Callback when bodies collide.

```java
public interface CollisionListener {
  void onCollisionBegin(RigidBody2D bodyA, RigidBody2D bodyB, 
                       CollisionPoint contact);
  
  void onCollisionEnd(RigidBody2D bodyA, RigidBody2D bodyB);
}
```

**Usage:**

```java
world.addCollisionListener(new CollisionListener() {
  @Override
  public void onCollisionBegin(RigidBody2D a, RigidBody2D b, 
                               CollisionPoint contact) {
    // Hero hit enemy
    if (isHero(a) && isEnemy(b)) {
      hero.takeDamage(10);
    }
    // Enemy hit ground
    if (isEnemy(a) && isGround(b)) {
      a.setAngularVelocity(0);  // stop spinning
    }
  }
  
  @Override
  public void onCollisionEnd(RigidBody2D a, RigidBody2D b) {
    // Bodies separated (e.g., jump ending)
    if (isHero(a) && isGround(b)) {
      isJumping = false;
    }
  }
});
```

---

### RaycastQuery

**Location:** `modules/core/src/main/java/com/jvn/core/physics/RaycastQuery.java`

Line-of-sight queries and hitscan.

```java
public class Ray {
  public Ray(double x1, double y1, double x2, double y2);
  
  public double getStartX();
  public double getStartY();
  public double getEndX();
  public double getEndY();
}

public class RaycastHit {
  public RigidBody2D getBody();
  public double getX();    // intersection point
  public double getY();
  public double getFraction();  // 0.0 to 1.0 along ray
  public double getNormalX();   // surface normal
  public double getNormalY();
}
```

**Filtering:**

```java
// Query only enemies and pickups, skip terrain
int mask = CollisionGroup.ENEMIES | CollisionGroup.PICKUPS;
List<RaycastHit> hits = world.raycast(ray, mask);

// Usage: hitscan weapon
Ray bulletPath = new Ray(gunX, gunY, gunX + 100, gunY);
List<RaycastHit> targets = world.raycast(bulletPath, CollisionGroup.ENEMIES);
if (!targets.isEmpty()) {
  RaycastHit hit = targets.get(0);  // first hit
  enemies.get(hit.getBody()).takeDamage(50);
}
```

---

## Collision Groups

Efficient broad-phase filtering using bitmasks.

```java
public enum CollisionGroup {
  TERRAIN(1 << 0),      // 0x0001
  PLAYER(1 << 1),       // 0x0002
  ENEMIES(1 << 2),      // 0x0004
  PICKUPS(1 << 3),      // 0x0008
  PROJECTILES(1 << 4),  // 0x0010
}
```

**Configuration:**

```java
BodyDef heroDef = new BodyDef(BodyType.DYNAMIC);
heroDef.collisionGroup(CollisionGroup.PLAYER);
heroDef.collisionMask(
  CollisionGroup.TERRAIN | 
  CollisionGroup.ENEMIES | 
  CollisionGroup.PICKUPS
);
RigidBody2D hero = world.createBody(heroDef);

// Hero collides with terrain, enemies, pickups
// Hero does NOT collide with other players, projectiles
```

---

## Common Scenarios

### Jumping/Platformer

```java
// Check if on ground
RigidBody2D groundBody = world.getBodyAt(heroX, heroY + heroHeight/2 + 1);
if (groundBody != null && isGround(groundBody)) {
  canJump = true;
}

// Jump input
if (input.isJumpPressed() && canJump) {
  hero.applyImpulse(0, -jumpForce);
  canJump = false;
}
```

### Hitscan Weapon

```java
// Ray from gun barrel to max range
Vector2 aim = hero.getAimDirection();  // normalized
Ray bulletPath = new Ray(
  gunX, gunY,
  gunX + aim.x * 500, gunY + aim.y * 500  // 500 unit range
);

// Find targets
List<RaycastHit> hits = world.raycast(bulletPath, CollisionGroup.ENEMIES);
for (RaycastHit hit : hits) {
  Enemy enemy = enemies.get(hit.getBody());
  enemy.takeDamage(50);
}

// Visual: draw line from gun to first hit
if (!hits.isEmpty()) {
  RaycastHit firstHit = hits.get(0);
  drawLine(gunX, gunY, firstHit.getX(), firstHit.getY(), color.red);
}
```

### Pushback/Knockback

```java
// Enemy hit by projectile
world.addCollisionListener(new CollisionListener() {
  @Override
  public void onCollisionBegin(RigidBody2D a, RigidBody2D b, 
                               CollisionPoint contact) {
    if (isProjectile(a) && isEnemy(b)) {
      // Apply knockback
      double knockbackForce = 20;
      double dx = contact.getNormalX();
      double dy = contact.getNormalY();
      b.applyImpulse(dx * knockbackForce, dy * knockbackForce);
    }
  }
});
```

---

## Performance Optimization

### Broad-Phase

Use collision groups to reduce checks:

```java
// GOOD: only terrain collides with terrain
terrainBody.collisionMask(CollisionGroup.TERRAIN);

// BAD: terrain collides with everything
terrainBody.collisionMask(0xFFFF);  // all bits set
```

### Sleeping

Inactive bodies are "put to sleep" by physics engine:

```java
// Engine automatically sleeps idle bodies
// Wake on collision or force application
if (body.isAwake()) {
  // Physics being computed
} else {
  // No forces, resting — cheap to iterate
}
```

### Iteration Count

Lower iteration counts = faster but less accurate:

```java
world.setIterations(4, 8);  // fast, less stable
world.setIterations(6, 10);  // default
world.setIterations(10, 20);  // accurate but slow
```

---

## Testing

From tests (conceptual):

```java
@Test
void testGravity() {
  PhysicsWorld2D world = new Box2DWorld();
  world.setGravity(0, 9.8);
  
  RigidBody2D ball = world.createBody(new BodyDef(DYNAMIC));
  ball.setPosition(0, 0);
  ball.setMass(1);
  
  world.update(1.0);  // 1 second
  
  // Should have fallen ~49 units (v = gt, s = 0.5*g*t^2)
  assertEquals(-49, ball.getY(), 0.1);
}

@Test
void testCollisionDetection() {
  // ... setup bodies ...
  
  final boolean[] collided = {false};
  world.addCollisionListener(new CollisionListener() {
    @Override
    public void onCollisionBegin(RigidBody2D a, RigidBody2D b, 
                                 CollisionPoint contact) {
      collided[0] = true;
    }
  });
  
  world.update(0.016);  // step
  assertTrue(collided[0]);
}
```

---

## Related Documentation

- **2D Engine:** [docs/architecture/core/2d-engine.md](2d-engine.md) — entities and rendering
- **JES Physics:** [docs/scripting/jes/systems/jes-physics.md](../../scripting/jes/systems/jes-physics.md) — high-level component wrapper
- **System Architecture:** [docs/architecture/core/system-architecture.md](system-architecture.md) — how physics fits
- **Performance Tips:** [docs/architecture/quality/performance.md](../quality/performance.md)

---

**Last Updated:** May 2026  
**Implementation:** Box2D-compatible (uses JBox2D or direct Box2D Java binding)
