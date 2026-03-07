#pragma once
#include "raylib.h"
#include "core/Vector2D.hpp"
#include "physics/Cape.hpp"
#include "physics/WindField.hpp"
#include "entities/Character.hpp"
#include "entities/FlightController.hpp"
#include "utils/PerformanceMonitor.hpp"

namespace ethereal {

struct RenderConfig {
    int screenWidth = 1280;
    int screenHeight = 720;
    Color backgroundColor = {15, 20, 35, 255};
    Color capeColor = {230, 180, 140, 255};
    Color capeHighlight = {255, 220, 180, 255};
    Color characterColor = {255, 245, 230, 255};
    Color windColor = {100, 150, 200, 60};
    Color uiColor = {255, 255, 255, 200};
    bool showWindField = true;
    bool showDebug = false;
    int windGridSize = 40;
};

class Renderer {
public:
    Renderer();
    Renderer(const RenderConfig& config);
    ~Renderer();

    void initialize();
    void shutdown();
    
    void beginFrame();
    void endFrame();
    
    void drawBackground(float time);
    void drawWindField(const WindField& wind, const Vector2D& cameraOffset);
    void drawCape(const Cape& cape, const Vector2D& cameraOffset);
    void drawCharacter(const Character& character, const Vector2D& cameraOffset);
    void drawUI(const FlightController& flight, const PerformanceMonitor& perf);
    void drawParticles(const WindField& wind, float time, const Vector2D& cameraOffset);
    
    bool shouldClose() const;
    
    Vector2D getScreenCenter() const;
    const RenderConfig& getConfig() const { return config; }
    void setConfig(const RenderConfig& cfg) { config = cfg; }

private:
    RenderConfig config;
    bool initialized;
    
    struct FloatingParticle {
        Vector2D position;
        Vector2D velocity;
        float alpha;
        float size;
        float lifetime;
    };
    std::vector<FloatingParticle> particles;
    
    void drawCapeSegment(const Vector2D& p1, const Vector2D& p2, 
                         const Vector2D& p3, const Vector2D& p4,
                         float depth, const Vector2D& offset);
    Color lerpColor(Color a, Color b, float t) const;
    void updateParticles(float dt, const WindField& wind);
};

} // namespace ethereal
