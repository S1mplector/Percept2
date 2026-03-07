#include "raylib.h"
#include "core/Vector3D.hpp"
#include "core/Quaternion.hpp"
#include "physics/WindField3D.hpp"
#include "entities/Character3D.hpp"
#include "entities/Camera3D.hpp"
#include "entities/FlightController3D.hpp"
#include "environment/Terrain.hpp"
#include "rendering/Renderer3D.hpp"
#include "rendering/EnergyBeingRenderer.hpp"
#include "rendering/EnvironmentRenderer.hpp"
#include "audio/WindSoundSynthesizer.hpp"
#include "utils/PerformanceMonitor.hpp"

using namespace ethereal;

int main() {
    RenderConfig3D renderConfig;
    renderConfig.screenWidth = 1280;
    renderConfig.screenHeight = 720;
    
    // Warm desert sky colors
    renderConfig.skyColorTop = {120, 170, 215, 255};
    renderConfig.skyColorBottom = {250, 240, 225, 255};
    renderConfig.sunColor = {255, 250, 235, 255};
    renderConfig.cloudColor = {255, 255, 255, 50};
    renderConfig.sunDirection = Vector3D(0.3f, 0.85f, 0.15f);
    
    // Cape colors - warm red/orange gradient
    renderConfig.capeColorInner = {200, 80, 60, 255};
    renderConfig.capeColorOuter = {255, 140, 90, 255};
    
    // Trail color
    renderConfig.trailColor = {255, 220, 180, 200};
    
    Renderer3D renderer(renderConfig);
    renderer.initialize();

    // Wind disabled by default for calm flight
    WindConfig3D windConfig;
    windConfig.baseStrength = 0.0f;
    windConfig.gustStrength = 0.0f;
    windConfig.turbulence = 0.0f;
    windConfig.noiseScale = 0.004f;
    windConfig.timeScale = 0.3f;
    windConfig.baseDirection = Vector3D(0.0f, 0.0f, 0.0f);
    windConfig.curlStrength = 0.0f;
    
    WindField3D wind(windConfig);

    Vector3D startPos(0.0f, 100.0f, 0.0f);
    
    CharacterConfig3D charConfig;
    charConfig.radius = 6.0f;
    charConfig.maxSpeed = 160.0f;
    charConfig.acceleration = 120.0f;
    charConfig.drag = 0.985f;
    charConfig.trailLength = 20;
    charConfig.rotationSpeed = 5.0f;
    
    Character3D character(startPos, charConfig);

    // Energy Being renderer (procedural orbs that merge/separate)
    EnergyBeingConfig energyConfig;
    energyConfig.coreOrbs = 5;
    energyConfig.midOrbs = 8;
    energyConfig.outerOrbs = 12;
    energyConfig.coreRadius = 2.2f;
    energyConfig.midRadius = 1.6f;
    energyConfig.outerRadius = 1.0f;
    energyConfig.coreSpread = 2.5f;
    energyConfig.midSpread = 6.0f;
    energyConfig.outerSpread = 10.0f;
    energyConfig.mergeSpeed = 5.0f;
    energyConfig.separateSpeed = 7.0f;
    energyConfig.rotationSpeed = 1.8f;
    energyConfig.flowSpeed = 2.2f;
    energyConfig.glowIntensity = 0.85f;
    
    EnergyBeingRenderer energyBeing(energyConfig);
    energyBeing.initialize();
    
    // Environment renderer - NIGHT SCENE
    EnvironmentConfig envConfig;
    envConfig.skyColorZenith = {5, 8, 18, 255};       // Deep dark blue
    envConfig.skyColorHorizon = {15, 22, 40, 255};    // Slightly lighter at horizon
    envConfig.fogStart = 200.0f;
    envConfig.fogEnd = 900.0f;
    envConfig.fogColor = {12, 15, 28, 255};           // Dark blue fog
    envConfig.cloudLayers = 2;
    envConfig.cloudsPerLayer = 5;
    envConfig.atmosphereParticles = 120;
    
    // Moon settings
    envConfig.enableMoon = true;
    envConfig.moonDirection = Vector3D(-0.4f, 0.65f, 0.4f);
    envConfig.moonSize = 30.0f;
    envConfig.moonGlowSize = 100.0f;
    envConfig.starCount = 400;
    envConfig.starBrightness = 0.95f;
    
    EnvironmentRenderer envRenderer(envConfig);
    envRenderer.initialize();

    // Smooth, responsive flight with mouse controls
    FlightConfig3D flightConfig;
    flightConfig.liftForce = 90.0f;
    flightConfig.diveForce = 40.0f;
    flightConfig.horizontalForce = 85.0f;
    flightConfig.glideRatio = 3.5f;
    flightConfig.windAssist = 0.0f;
    flightConfig.mouseSensitivity = 0.002f;
    flightConfig.turnSmoothing = 6.0f;
    flightConfig.thrustAcceleration = 100.0f;
    flightConfig.thrustMaxSpeed = 180.0f;
    flightConfig.climbSensitivity = 0.6f;
    
    FlightController3D flight(&character, flightConfig);
    
    // Hide and capture mouse cursor for mouse look
    DisableCursor();

    // Camera locked behind player
    FlightCameraConfig cameraConfig;
    cameraConfig.followDistance = 55.0f;
    cameraConfig.followHeight = 18.0f;
    cameraConfig.smoothSpeed = 6.0f;
    cameraConfig.fov = 65.0f;
    
    FlightCamera camera(startPos + Vector3D(0, 30, 80), startPos, cameraConfig);

    // Procedural terrain - dramatic mountains with desert sand
    TerrainConfig terrainConfig;
    terrainConfig.gridSize = 100;
    terrainConfig.tileSize = 18.0f;
    terrainConfig.maxHeight = 350.0f;
    terrainConfig.mountainFrequency = 0.004f;
    terrainConfig.duneFrequency = 0.015f;
    terrainConfig.mountainPower = 2.2f;
    terrainConfig.duneAmplitude = 18.0f;
    terrainConfig.mountainOctaves = 6;
    terrainConfig.duneOctaves = 4;
    terrainConfig.baseHeight = -80.0f;
    
    // Warm sand and cool rock colors
    terrainConfig.sandColorLight = {245, 230, 200, 255};
    terrainConfig.sandColorDark = {215, 190, 155, 255};
    terrainConfig.rockColorLight = {175, 155, 140, 255};
    terrainConfig.rockColorDark = {110, 95, 85, 255};
    terrainConfig.peakColor = {255, 250, 245, 255};
    terrainConfig.rockThreshold = 0.40f;
    terrainConfig.peakThreshold = 0.78f;
    
    Terrain terrain(terrainConfig);
    terrain.generate(12345);

    // Procedural wind sound synthesizer
    WindSoundConfig windSoundConfig;
    windSoundConfig.masterVolume = 0.65f;
    windSoundConfig.lowWindVolume = 0.5f;
    windSoundConfig.midWindVolume = 0.6f;
    windSoundConfig.highWindVolume = 0.35f;
    windSoundConfig.gustVolume = 0.45f;
    windSoundConfig.speedInfluence = 0.85f;
    windSoundConfig.windInfluence = 0.5f;
    windSoundConfig.altitudeInfluence = 0.35f;
    windSoundConfig.gustRate = 0.12f;
    
    WindSoundSynthesizer windSound(windSoundConfig);
    windSound.initialize();

    PerformanceMonitor perfMonitor;
    float time = 0.0f;
    bool showWindDebug = false;

    while (!renderer.shouldClose()) {
        perfMonitor.beginFrame();
        
        float dt = GetFrameTime();
        dt = std::min(dt, 0.033f);
        time += dt;

        // === MOUSE + LEFT-CLICK CONTROLS ===
        // Mouse movement controls direction
        // Left-click (hold) to fly/thrust forward
        // Release to glide naturally
        
        Vector2 mouseDelta = GetMouseDelta();
        bool isFlying = IsMouseButtonDown(MOUSE_BUTTON_LEFT);
        
        // Double-click detection for boost
        static float lastClickTime = 0.0f;
        static bool wasClicked = false;
        if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            float currentTime = GetTime();
            if (currentTime - lastClickTime < 0.3f && wasClicked) {
                flight.boost();
                wasClicked = false;
            } else {
                wasClicked = true;
            }
            lastClickTime = currentTime;
        }
        
        // Right-click for quick ascent (optional)
        if (IsMouseButtonDown(MOUSE_BUTTON_RIGHT) && flight.getEnergy() > 0) {
            character.applyForce(Vector3D(0, 120.0f, 0));
        }
        
        if (IsKeyPressed(KEY_V)) {
            showWindDebug = !showWindDebug;
            RenderConfig3D cfg = renderer.getConfig();
            cfg.showWindDebug = showWindDebug;
            renderer.setConfig(cfg);
        }
        
        if (IsKeyPressed(KEY_R)) {
            character.setPosition(Vector3D(0, 100, 0));
            character.setVelocity(Vector3D::zero());
        }
        
        if (IsKeyPressed(KEY_M)) {
            windSound.setEnabled(!windSound.isEnabled());
        }
        
        if (IsKeyPressed(KEY_ESCAPE)) {
            EnableCursor();
        }

        wind.update(dt);
        
        // Use mouse-based flight control
        flight.updateMouseControl(mouseDelta.x, mouseDelta.y, isFlying, dt);
        flight.update(dt, wind);
        character.update(dt);
        
        // Update wind sound based on game state
        float playerSpeed = character.getSpeed();
        float windIntensity = wind.getWindAt(character.getPosition()).length();
        float altitude = character.getPosition().y;
        windSound.update(dt, playerSpeed, windIntensity, altitude);

        // Ground collision with terrain
        Vector3D pos = character.getPosition();
        float groundHeight = terrain.getHeightAt(pos.x, pos.z) + character.getRadius() + 2.0f;
        if (pos.y < groundHeight) {
            pos.y = groundHeight;
            character.setPosition(pos);
            
            // Dampen vertical velocity on ground contact
            Vector3D vel = character.getVelocity();
            if (vel.y < 0) {
                vel.y *= -0.3f;  // Small bounce
                character.setVelocity(vel);
            }
        }

        // Update energy being
        energyBeing.update(dt, character);
        
        // Update environment
        envRenderer.update(dt, camera.getPosition(), wind);

        camera.followTarget(character.getPosition(), character.getVelocity(), dt);

        renderer.beginFrame(camera);
        
        // Use new environment renderer - NIGHT SCENE
        envRenderer.renderSky(camera, time);
        envRenderer.renderMoonAndStars(camera, time);
        envRenderer.renderDistantMountains(camera, time);
        envRenderer.renderTerrain(terrain, camera);
        envRenderer.renderAtmosphere(camera, dt);
        
        renderer.drawWindField(wind, character.getPosition());
        
        // Render energy being (replaces character + cape)
        BeginMode3D({
            {camera.getPosition().x, camera.getPosition().y, camera.getPosition().z},
            {camera.getTarget().x, camera.getTarget().y, camera.getTarget().z},
            {0, 1, 0}, 65.0f, CAMERA_PERSPECTIVE
        });
        energyBeing.render(character);
        EndMode3D();
        
        renderer.drawUI(flight, perfMonitor, camera);

        if (IsKeyDown(KEY_TAB)) {
            Vector3D pos = character.getPosition();
            Vector3D vel = character.getVelocity();
            DrawText(TextFormat("Pos: %.0f, %.0f, %.0f", pos.x, pos.y, pos.z), 20, 160, 14, WHITE);
            DrawText(TextFormat("Vel: %.0f, %.0f, %.0f", vel.x, vel.y, vel.z), 20, 180, 14, WHITE);
            DrawText(TextFormat("Speed: %.0f", character.getSpeed()), 20, 200, 14, WHITE);
            DrawText(TextFormat("Glide Eff: %.0f%%", flight.getGlideEfficiency() * 100), 20, 220, 14, WHITE);
        }
        
        renderer.endFrame();
        perfMonitor.endFrame();
    }

    windSound.shutdown();
    renderer.shutdown();
    return 0;
}
