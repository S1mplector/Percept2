#pragma once
#include "raylib.h"
#include "core/Vector3D.hpp"
#include "environment/Terrain.hpp"
#include "entities/Camera3D.hpp"
#include "physics/WindField3D.hpp"
#include <vector>

namespace ethereal {

struct EnvironmentConfig {
    // Sky (night mode)
    Color skyColorZenith = {8, 12, 25, 255};
    Color skyColorHorizon = {20, 30, 50, 255};
    Color sunColor = {255, 250, 240, 255};
    Vector3D sunDirection = Vector3D(0.4f, 0.7f, 0.3f);
    float sunSize = 35.0f;
    float sunGlowSize = 120.0f;
    
    // Moon
    bool enableMoon = true;
    Vector3D moonDirection = Vector3D(-0.3f, 0.6f, 0.5f);
    Color moonColor = {220, 230, 255, 255};
    float moonSize = 25.0f;
    float moonGlowSize = 80.0f;
    
    // Stars
    int starCount = 300;
    float starBrightness = 0.9f;
    
    // Fog
    float fogDensity = 0.0008f;
    float fogStart = 150.0f;
    float fogEnd = 1200.0f;
    Color fogColor = {230, 220, 210, 255};
    
    // Clouds
    int cloudLayers = 3;
    int cloudsPerLayer = 8;
    float cloudBaseHeight = 200.0f;
    float cloudLayerSpacing = 60.0f;
    
    // Terrain
    float terrainViewDistance = 1000.0f;
    float terrainLodDistance = 400.0f;
    bool smoothShading = true;
    
    // Atmosphere particles
    int atmosphereParticles = 200;
    float particleSpawnRadius = 400.0f;
};

struct AtmosphereParticle3D {
    Vector3D position;
    Vector3D velocity;
    float size;
    float alpha;
    float lifetime;
    int type; // 0=dust, 1=sparkle, 2=wisp
};

class EnvironmentRenderer {
public:
    EnvironmentRenderer();
    explicit EnvironmentRenderer(const EnvironmentConfig& config);
    
    void initialize();
    void update(float dt, const Vector3D& cameraPos, const WindField3D& wind);
    
    void renderSky(const FlightCamera& camera, float time);
    void renderMoonAndStars(const FlightCamera& camera, float time);
    void renderTerrain(const Terrain& terrain, const FlightCamera& camera);
    void renderClouds(const FlightCamera& camera, float time);
    void renderAtmosphere(const FlightCamera& camera, float dt);
    void renderDistantMountains(const FlightCamera& camera, float time);
    
    void setConfig(const EnvironmentConfig& cfg) { config = cfg; }
    const EnvironmentConfig& getConfig() const { return config; }

private:
    EnvironmentConfig config;
    std::vector<AtmosphereParticle3D> particles;
    std::vector<Vector3D> starPositions;
    std::vector<float> starBrightnesses;
    float time;
    
    void initParticles(const Vector3D& center);
    void initStars();
    void updateParticles(float dt, const Vector3D& cameraPos, const WindField3D& wind);
    
    Color blendColors(Color a, Color b, float t);
    Color applyFog(Color color, float distance, float height);
    Color getTerrainColor(float height, float steepness);
    float calculateLighting(const Vector3D& normal);
};

} // namespace ethereal
