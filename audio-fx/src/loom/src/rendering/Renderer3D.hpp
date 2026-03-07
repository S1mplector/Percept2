#pragma once
#include "raylib.h"
#include "core/Vector3D.hpp"
#include "core/Matrix4.hpp"
#include "physics/Cape3D.hpp"
#include "physics/WindField3D.hpp"
#include "entities/Character3D.hpp"
#include "entities/Camera3D.hpp"
using FlightCamera = ethereal::FlightCamera;
#include "entities/FlightController3D.hpp"
#include "environment/Terrain.hpp"
#include "utils/PerformanceMonitor.hpp"
#include <vector>

namespace ethereal {

struct RenderConfig3D {
    int screenWidth = 1280;
    int screenHeight = 720;
    Color skyColorTop = {70, 130, 180, 255};
    Color skyColorBottom = {200, 220, 240, 255};
    Color sunColor = {255, 250, 240, 255};
    Color capeColorInner = {230, 180, 140, 255};
    Color capeColorOuter = {255, 220, 180, 255};
    Color characterColor = {255, 248, 240, 255};
    Color trailColor = {255, 240, 220, 180};
    Color cloudColor = {255, 255, 255, 100};
    Color groundColor = {60, 80, 60, 255};
    bool showWindDebug = false;
    bool showWireframe = false;
    int particleCount = 300;
    float fogDensity = 0.001f;
    Vector3D sunDirection = Vector3D(0.5f, 0.8f, 0.3f);
};

struct AtmosphereParticle {
    Vector3D position;
    Vector3D velocity;
    float alpha;
    float size;
    float lifetime;
    int type;
};

class Renderer3D {
public:
    Renderer3D();
    explicit Renderer3D(const RenderConfig3D& config);
    ~Renderer3D();

    void initialize();
    void shutdown();
    
    void beginFrame(const FlightCamera& camera);
    void endFrame();
    
    void drawSky(const FlightCamera& camera, float time);
    void drawGround(const FlightCamera& camera);
    void drawTerrain(const Terrain& terrain, const FlightCamera& camera);
    void drawClouds(const FlightCamera& camera, float time);
    void drawCape(const Cape3D& cape);
    void drawCharacter(const Character3D& character);
    void drawTrail(const Character3D& character);
    void drawWindField(const WindField3D& wind, const Vector3D& center);
    void drawAtmosphere(const WindField3D& wind, float dt, const FlightCamera& camera);
    void drawUI(const FlightController3D& flight, const PerformanceMonitor& perf, const FlightCamera& camera);
    
    bool shouldClose() const;
    float getAspectRatio() const;
    
    const RenderConfig3D& getConfig() const { return config; }
    void setConfig(const RenderConfig3D& cfg) { config = cfg; }

private:
    RenderConfig3D config;
    bool initialized;
    
    ::Camera3D raylibCamera;
    std::vector<AtmosphereParticle> particles;
    
    void initParticles();
    void updateParticles(float dt, const WindField3D& wind, const FlightCamera& camera);
    Color applyFog(Color color, float distance) const;
    void drawCapeMesh(const Cape3D& cape);
};

} // namespace ethereal
