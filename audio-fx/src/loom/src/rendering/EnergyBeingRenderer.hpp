#pragma once
#include "raylib.h"
#include "core/Vector3D.hpp"
#include "entities/Character3D.hpp"
#include <vector>
#include <cmath>

namespace ethereal {

struct EnergyOrb {
    Vector3D localPosition;      // Position relative to center
    Vector3D targetPosition;     // Where the orb wants to be
    Vector3D velocity;           // Current velocity for smooth movement
    float radius;                // Base radius
    float phase;                 // Animation phase offset
    float brightness;            // 0-1 brightness multiplier
    int layer;                   // Which layer (core, mid, outer)
};

struct EnergyBeingConfig {
    // Orb counts per layer
    int coreOrbs = 5;
    int midOrbs = 8;
    int outerOrbs = 12;
    
    // Sizes
    float coreRadius = 2.5f;
    float midRadius = 1.8f;
    float outerRadius = 1.2f;
    
    // Spread distances
    float coreSpread = 2.0f;
    float midSpread = 5.0f;
    float outerSpread = 9.0f;
    
    // Movement response
    float mergeSpeed = 4.0f;         // How fast orbs merge when still
    float separateSpeed = 6.0f;      // How fast orbs separate when moving
    float rotationSpeed = 1.5f;      // Base rotation speed
    float flowSpeed = 2.0f;          // Flow animation speed
    
    // Visual
    float glowIntensity = 0.8f;
    float pulseSpeed = 2.0f;
    float trailLength = 0.3f;        // Trail behind moving orbs
    
    // Colors (warm energy palette)
    Color coreColor = {255, 255, 250, 255};
    Color midColor = {255, 245, 220, 220};
    Color outerColor = {255, 235, 200, 180};
    Color glowColor = {255, 220, 180, 100};
};

class EnergyBeingRenderer {
public:
    EnergyBeingRenderer();
    explicit EnergyBeingRenderer(const EnergyBeingConfig& config);
    
    void initialize();
    void update(float dt, const Character3D& character);
    void render(const Character3D& character);
    
    void setConfig(const EnergyBeingConfig& cfg) { config = cfg; }
    const EnergyBeingConfig& getConfig() const { return config; }

private:
    EnergyBeingConfig config;
    std::vector<EnergyOrb> orbs;
    float time;
    float lastSpeed;
    Vector3D lastPosition;
    Vector3D smoothedVelocity;
    
    void createOrbs();
    void updateOrbPositions(float dt, float speed, const Vector3D& velocity);
    void renderOrb(const EnergyOrb& orb, const Vector3D& center, float speedFactor);
    void renderGlow(const Vector3D& center, float speedFactor);
    void renderConnections(const Vector3D& center, float speedFactor);
    
    // Smooth interpolation helpers
    float smoothstep(float edge0, float edge1, float x);
    Vector3D lerpVector(const Vector3D& a, const Vector3D& b, float t);
};

} // namespace ethereal
