#include "raylib.h"
#include "core/Vector2D.hpp"
#include "physics/WindField.hpp"
#include "physics/Cape.hpp"
#include "entities/Character.hpp"
#include "entities/FlightController.hpp"
#include "rendering/Renderer.hpp"
#include "utils/PerformanceMonitor.hpp"

using namespace ethereal;

int main() {
    RenderConfig renderConfig;
    renderConfig.screenWidth = 1280;
    renderConfig.screenHeight = 720;
    
    Renderer renderer(renderConfig);
    renderer.initialize();

    WindConfig windConfig;
    windConfig.baseStrength = 60.0f;
    windConfig.gustStrength = 100.0f;
    windConfig.turbulence = 0.4f;
    windConfig.noiseScale = 0.006f;
    windConfig.timeScale = 0.4f;
    windConfig.baseDirection = Vector2D(1.0f, 0.1f);
    
    WindField wind(windConfig);

    Vector2D startPos = renderer.getScreenCenter();
    
    CharacterConfig charConfig;
    charConfig.radius = 18.0f;
    charConfig.maxSpeed = 700.0f;
    charConfig.acceleration = 900.0f;
    charConfig.drag = 0.985f;
    
    Character character(startPos, charConfig);

    CapeConfig capeConfig;
    capeConfig.segments = 14;
    capeConfig.width = 10;
    capeConfig.segmentLength = 7.0f;
    capeConfig.stiffness = 0.92f;
    capeConfig.bendStiffness = 0.25f;
    capeConfig.gravity = 350.0f;
    capeConfig.windInfluence = 1.4f;
    capeConfig.damping = 0.985f;
    
    Cape cape(character.getCapeAttachPoint(), capeConfig);

    FlightConfig flightConfig;
    flightConfig.liftForce = 600.0f;
    flightConfig.diveForce = 350.0f;
    flightConfig.horizontalForce = 500.0f;
    flightConfig.glideRatio = 2.8f;
    flightConfig.windAssist = 0.9f;
    
    FlightController flight(&character, flightConfig);

    PerformanceMonitor perfMonitor;

    Vector2D cameraOffset = Vector2D::zero();
    float cameraSmoothing = 0.08f;
    float time = 0.0f;
    bool showWindField = true;

    while (!renderer.shouldClose()) {
        perfMonitor.beginFrame();
        
        float dt = GetFrameTime();
        dt = std::min(dt, 0.033f);
        time += dt;

        flight.stopVertical();
        flight.stopHorizontal();
        
        if (IsKeyDown(KEY_W) || IsKeyDown(KEY_UP)) {
            flight.moveUp();
        }
        if (IsKeyDown(KEY_S) || IsKeyDown(KEY_DOWN)) {
            flight.moveDown();
        }
        if (IsKeyDown(KEY_A) || IsKeyDown(KEY_LEFT)) {
            flight.moveLeft();
        }
        if (IsKeyDown(KEY_D) || IsKeyDown(KEY_RIGHT)) {
            flight.moveRight();
        }
        
        if (IsKeyDown(KEY_SPACE)) {
            Vector2D boostDir = Vector2D::fromAngle(character.getFacingAngle(), 200.0f);
            character.applyForce(boostDir);
        }
        
        if (IsKeyPressed(KEY_V)) {
            showWindField = !showWindField;
            RenderConfig cfg = renderer.getConfig();
            cfg.showWindField = showWindField;
            renderer.setConfig(cfg);
        }
        
        if (IsKeyPressed(KEY_R)) {
            character.setPosition(renderer.getScreenCenter());
            character.setVelocity(Vector2D::zero());
        }

        wind.update(dt);
        flight.update(dt, wind);
        character.update(dt);

        cape.setAttachPoint(character.getCapeAttachPoint());
        cape.setAttachVelocity(character.getVelocity());
        cape.update(dt, wind);
        cape.solveConstraints(5);

        Vector2D targetOffset = character.getPosition() - renderer.getScreenCenter();
        cameraOffset = cameraOffset.lerp(targetOffset, cameraSmoothing);

        renderer.beginFrame();
        
        renderer.drawBackground(time);
        renderer.drawWindField(wind, cameraOffset);
        renderer.drawParticles(wind, time, cameraOffset);
        renderer.drawCape(cape, cameraOffset);
        renderer.drawCharacter(character, cameraOffset);
        renderer.drawUI(flight, perfMonitor);

        if (IsKeyDown(KEY_TAB)) {
            DrawText(TextFormat("Pos: %.0f, %.0f", character.getPosition().x, character.getPosition().y), 
                    20, 130, 14, WHITE);
            DrawText(TextFormat("Vel: %.0f, %.0f", character.getVelocity().x, character.getVelocity().y), 
                    20, 150, 14, WHITE);
            DrawText(TextFormat("Speed: %.0f", character.getSpeed()), 
                    20, 170, 14, WHITE);
            DrawText(TextFormat("Wind: %.0f, %.0f", 
                    wind.getWindAt(character.getPosition()).x,
                    wind.getWindAt(character.getPosition()).y), 
                    20, 190, 14, WHITE);
        }
        
        renderer.endFrame();
        perfMonitor.endFrame();
    }

    renderer.shutdown();
    return 0;
}
