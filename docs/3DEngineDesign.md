# Percept4 3D Engine - Design Document

## Goals

Build a modular 3D game engine on top of Percept4's existing architecture, supporting:
- 3D rendering with modern graphics pipeline
- Physics simulation
- Animation system
- Spatial audio
- Scene management
- Asset importing (models, textures)

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    Percept4 Core                     │
│  (Scene Management, Asset System, Engine Lifecycle)  │
└─────────────────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼────────┐ ┌────▼─────┐ ┌───────▼────────┐
│   3D Graphics  │ │  Physics │ │   3D Audio     │
│   - Renderer   │ │  - World  │ │  - Spatial     │
│   - Camera     │ │  - Bodies │ │  - Attenuation │
│   - Lighting   │ │  - Forces │ │                │
└────────────────┘ └──────────┘ └────────────────┘
        │                │                │
        └────────────────┼────────────────┘
                         │
               ┌─────────▼──────────┐
               │   JavaFX 3D / LWJGL │
               │   (Rendering API)   │
               └─────────────────────┘
```

## 📦 Module Structure

### `core-3d` - 3D Math & Data Structures
- **Math**
  - `Vector3f` - 3D vectors with operations
  - `Matrix4f` - 4x4 transformation matrices
  - `Quaternion` - Rotation representation
  - `Transform` - Position, rotation, scale
  - `AABB` - Axis-aligned bounding box
  - `Ray` - Ray casting for picking

- **Geometry**
  - `Mesh` - Vertex and index data
  - `Vertex` - Position, normal, UV, color
  - `SubMesh` - Material-specific geometry
  - `BoundingVolume` - Collision bounds

- **Scene Graph**
  - `SceneNode` - Hierarchical node structure
  - `Entity` - Game object with components
  - `Component` - Behavior attachment system

### `renderer-3d` - Graphics Rendering
- **Core Renderer**
  - `Renderer3D` - Main rendering pipeline
  - `RenderQueue` - Sorted render batches
  - `Shader` - Shader program wrapper
  - `Material` - Surface properties
  - `Texture` - 2D/Cubemap textures

- **Camera**
  - `Camera` - View and projection
  - `PerspectiveCamera` - 3D camera
  - `OrthographicCamera` - 2D/UI camera
  - `CameraController` - Input handling

- **Lighting**
  - `Light` - Base light class
  - `DirectionalLight` - Sun-like light
  - `PointLight` - Omni-directional
  - `SpotLight` - Cone-shaped light
  - `AmbientLight` - Global illumination

- **Effects**
  - `Skybox` - Environment rendering
  - `PostProcess` - Screen effects
  - `ParticleSystem` - Particle effects
  - `Billboard` - Screen-facing sprites

### `physics-3d` - Physics Simulation
- **Dynamics**
  - `PhysicsWorld` - Simulation context
  - `RigidBody` - Physical object
  - `Collider` - Collision shape
  - `PhysicsMaterial` - Friction, bounce

- **Collision**
  - `CollisionDetection` - Broad/narrow phase
  - `Contact` - Collision contact point
  - `CollisionShape` - Box, sphere, mesh

### `assets-3d` - 3D Asset Loading
- **Loaders**
  - `ModelLoader` - Import 3D models
  - `TextureLoader` - Load images
  - `MaterialLoader` - Parse materials
  - **Formats**: OBJ, GLTF, FBX (planned)

### `animation-3d` - Animation System
- **Skeletal Animation**
  - `Skeleton` - Bone hierarchy
  - `Animation` - Keyframe data
  - `AnimationController` - Playback control
  - `Bone` - Transform bone

### `fx-3d` - JavaFX 3D Integration
- **Bridge**
  - `Fx3DLauncher` - JavaFX application
  - `Fx3DRenderer` - JavaFX 3D rendering
  - `Fx3DScene` - Scene3D wrapper

## 🎨 Rendering Pipeline

### Forward Rendering (Initial Implementation)
```
1. Update Phase
   └─> Update transforms, animations, physics

2. Culling Phase
   └─> Frustum culling, occlusion culling

3. Sorting Phase
   └─> Sort by material, distance, transparency

4. Geometry Pass
   ├─> Opaque objects (front-to-back)
   └─> Transparent objects (back-to-front)

5. Lighting Pass
   ├─> Per-object lighting calculations
   └─> Shadow mapping

6. Post-Processing
   ├─> Bloom, tone mapping
   ├─> Anti-aliasing (FXAA/MSAA)
   └─> Color grading

7. UI Overlay
   └─> 2D UI elements on top
```

### Deferred Rendering (Future)
- G-Buffer: Position, Normal, Albedo, Specular
- Lighting pass with multiple lights
- Better performance for many lights

## 🔧 Core 3D Math API

### Vector3f
```java
Vector3f position = new Vector3f(0, 0, 0);
Vector3f direction = new Vector3f(1, 0, 0).normalize();
float distance = position.distance(target);
Vector3f velocity = direction.multiply(speed);
```

### Matrix4f
```java
Matrix4f view = Matrix4f.lookAt(eye, target, up);
Matrix4f projection = Matrix4f.perspective(fov, aspect, near, far);
Matrix4f model = Matrix4f.translation(position)
    .multiply(Matrix4f.rotation(rotation))
    .multiply(Matrix4f.scale(scale));
Matrix4f mvp = projection.multiply(view).multiply(model);
```

### Transform
```java
Transform transform = new Transform();
transform.setPosition(new Vector3f(0, 1, 0));
transform.setRotation(Quaternion.fromEuler(0, 45, 0));
transform.setScale(new Vector3f(1, 1, 1));
Matrix4f worldMatrix = transform.toMatrix();
```

## 📐 Scene Management

### Scene Graph
```java
Scene3D scene = new Scene3D();

// Create entity with transform
Entity cube = new Entity("Cube");
cube.getComponent(Transform.class).setPosition(0, 1, 0);

// Add mesh renderer component
MeshRenderer renderer = new MeshRenderer();
renderer.setMesh(PrimitiveMesh.cube());
renderer.setMaterial(new StandardMaterial());
cube.addComponent(renderer);

// Add to scene
scene.addEntity(cube);

// Hierarchy
Entity parent = new Entity("Parent");
parent.addChild(cube); // cube transforms relative to parent
```

## 💡 Lighting System

### Light Types
```java
// Directional light (sun)
DirectionalLight sun = new DirectionalLight();
sun.setDirection(new Vector3f(-1, -1, -1).normalize());
sun.setColor(new Color(1, 1, 0.9f));
sun.setIntensity(1.0f);

// Point light (lamp)
PointLight lamp = new PointLight();
lamp.setPosition(new Vector3f(5, 3, 5));
lamp.setColor(Color.WHITE);
lamp.setIntensity(2.0f);
lamp.setRange(10.0f);

// Spot light (flashlight)
SpotLight flashlight = new SpotLight();
flashlight.setPosition(camera.getPosition());
flashlight.setDirection(camera.getForward());
flashlight.setConeAngle(30.0f);
flashlight.setIntensity(3.0f);
```

## 🎥 Camera System

### Camera Types
```java
// Perspective camera for 3D
PerspectiveCamera camera = new PerspectiveCamera();
camera.setFieldOfView(60.0f);
camera.setAspectRatio(16.0f / 9.0f);
camera.setNearPlane(0.1f);
camera.setFarPlane(1000.0f);

// Orthographic camera for 2D/UI
OrthographicCamera uiCamera = new OrthographicCamera();
uiCamera.setSize(1920, 1080);

// Camera controller
FlyController controller = new FlyController(camera);
controller.setMoveSpeed(5.0f);
controller.setLookSpeed(2.0f);
controller.update(deltaTime);
```

## 🎨 Material System

### Standard Material (PBR)
```java
StandardMaterial material = new StandardMaterial();
material.setAlbedo(new Color(0.8f, 0.2f, 0.2f));
material.setMetallic(0.5f);
material.setRoughness(0.3f);
material.setAlbedoMap(textureLoader.load("textures/brick.png"));
material.setNormalMap(textureLoader.load("textures/brick_normal.png"));
```

### Shader Material (Custom)
```java
ShaderMaterial material = new ShaderMaterial();
material.setShader(shaderLoader.load("shaders/custom.vert", "shaders/custom.frag"));
material.setUniform("time", time);
material.setUniform("color", new Vector3f(1, 0, 0));
```

## 🔄 Animation System

### Skeletal Animation
```java
// Load animated model
Model character = modelLoader.load("models/character.gltf");
Skeleton skeleton = character.getSkeleton();
AnimationController animator = new AnimationController(skeleton);

// Play animation
Animation walkAnim = character.getAnimation("walk");
animator.play(walkAnim);
animator.setSpeed(1.5f);
animator.setLoop(true);

// Blend animations
animator.crossfade("walk", "run", 0.3f);
```

## ⚡ Physics Integration

### Rigid Body Physics
```java
// Create physics world
PhysicsWorld physics = new PhysicsWorld();
physics.setGravity(new Vector3f(0, -9.81f, 0));

// Add rigid body to entity
RigidBody body = new RigidBody();
body.setMass(1.0f);
body.setCollider(new BoxCollider(new Vector3f(1, 1, 1)));
body.setPhysicsMaterial(new PhysicsMaterial(0.5f, 0.3f)); // friction, restitution
entity.addComponent(body);

// Apply forces
body.applyForce(new Vector3f(0, 100, 0));
body.applyImpulse(new Vector3f(0, 5, 0));
```

## 📦 Asset Pipeline

### Model Loading
```java
// Load 3D model
Model model = assetManager.load("models/house.obj", Model.class);

// Access meshes
for (Mesh mesh : model.getMeshes()) {
    MeshRenderer renderer = new MeshRenderer();
    renderer.setMesh(mesh);
    renderer.setMaterial(model.getMaterial(mesh));
}

// GLTF with animations
Model character = assetManager.load("models/character.gltf", Model.class);
Skeleton skeleton = character.getSkeleton();
List<Animation> animations = character.getAnimations();
```

## 🎮 Example: Complete 3D Scene

```java
public class Demo3DScene implements Scene {
    private Scene3D scene;
    private PerspectiveCamera camera;
    private FlyController controller;
    
    @Override
    public void onEnter() {
        scene = new Scene3D();
        
        // Setup camera
        camera = new PerspectiveCamera();
        camera.setPosition(new Vector3f(0, 2, 5));
        camera.lookAt(Vector3f.ZERO);
        controller = new FlyController(camera);
        
        // Add lights
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -1).normalize());
        scene.addLight(sun);
        
        // Create cube
        Entity cube = new Entity("Cube");
        cube.getComponent(Transform.class).setPosition(0, 1, 0);
        
        MeshRenderer cubeRenderer = new MeshRenderer();
        cubeRenderer.setMesh(PrimitiveMesh.cube());
        cubeRenderer.setMaterial(new StandardMaterial());
        cube.addComponent(cubeRenderer);
        
        RigidBody cubeBody = new RigidBody();
        cubeBody.setMass(1.0f);
        cubeBody.setCollider(new BoxCollider(Vector3f.ONE));
        cube.addComponent(cubeBody);
        
        scene.addEntity(cube);
        
        // Create ground plane
        Entity ground = createGround();
        scene.addEntity(ground);
    }
    
    @Override
    public void update(long deltaMs) {
        float deltaTime = deltaMs / 1000.0f;
        controller.update(deltaTime);
        scene.getPhysicsWorld().step(deltaTime);
        scene.update(deltaTime);
    }
    
    @Override
    public void render(Renderer renderer) {
        renderer.render(scene, camera);
    }
}
```

## 🚀 Implementation Phases

### Phase 1: Foundation (Current)
- [ ] 3D math library (Vector3f, Matrix4f, Quaternion)
- [ ] Transform system
- [ ] Basic mesh data structures
- [ ] Scene graph with entities

### Phase 2: Rendering
- [ ] JavaFX 3D renderer
- [ ] Basic shader system
- [ ] Camera implementation
- [ ] Simple lighting (ambient + directional)

### Phase 3: Assets
- [ ] OBJ model loader
- [ ] Texture loader
- [ ] Material system
- [ ] Primitive mesh generation

### Phase 4: Advanced Rendering
- [ ] Point lights and spot lights
- [ ] Shadow mapping
- [ ] Skybox
- [ ] Post-processing

### Phase 5: Physics
- [ ] Physics world
- [ ] Rigid body dynamics
- [ ] Collision detection
- [ ] Collision response

### Phase 6: Animation
- [ ] Skeletal animation
- [ ] Animation blending
- [ ] GLTF loader with animations

### Phase 7: Polish
- [ ] Particle systems
- [ ] LOD system
- [ ] Occlusion culling
- [ ] Performance optimization

## 📊 Technology Stack

### Rendering Backend Options

**Option 1: JavaFX 3D (Chosen)**
- ✅ Already integrated with engine
- ✅ Built-in 3D support
- ✅ Easy to use
- ⚠️ Limited shader control
- ⚠️ Performance limitations

**Option 2: LWJGL + OpenGL**
- ✅ Full control over rendering
- ✅ Better performance
- ✅ Modern OpenGL features
- ⚠️ More complex integration
- ⚠️ Platform-specific code

**Option 3: LWJGL + Vulkan**
- ✅ Best performance
- ✅ Explicit control
- ⚠️ Very complex
- ⚠️ Steep learning curve

**Decision: Start with JavaFX 3D, migrate to LWJGL if needed**

## 🎯 Success Criteria

The 3D engine is production-ready when:
- ✅ Can render 3D models with lighting
- ✅ Camera system works smoothly
- ✅ Scene graph with hierarchy
- ✅ Basic physics simulation
- ✅ Model loading (OBJ at minimum)
- ✅ Material system with textures
- ✅ Performance: 60 FPS with 1000+ objects
- ✅ Documentation and examples

---

**Next Steps**: Begin Phase 1 - Foundation (3D Math Library)
