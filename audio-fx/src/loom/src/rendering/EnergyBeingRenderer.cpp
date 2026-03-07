#include "EnergyBeingRenderer.hpp"
#include "rlgl.h"
#include <algorithm>
#include <random>

namespace ethereal {

EnergyBeingRenderer::EnergyBeingRenderer() : EnergyBeingRenderer(EnergyBeingConfig{}) {}

EnergyBeingRenderer::EnergyBeingRenderer(const EnergyBeingConfig& cfg)
    : config(cfg)
    , time(0.0f)
    , lastSpeed(0.0f)
    , lastPosition(Vector3D::zero())
    , smoothedVelocity(Vector3D::zero()) {
}

void EnergyBeingRenderer::initialize() {
    createOrbs();
}

void EnergyBeingRenderer::createOrbs() {
    orbs.clear();
    
    std::mt19937 rng(42);
    std::uniform_real_distribution<float> phaseDist(0.0f, 6.28318f);
    std::uniform_real_distribution<float> brightDist(0.8f, 1.0f);
    
    // Core orbs - tightest cluster
    for (int i = 0; i < config.coreOrbs; ++i) {
        EnergyOrb orb;
        float angle = (float)i / config.coreOrbs * 6.28318f;
        orb.localPosition = Vector3D(
            std::cos(angle) * config.coreSpread * 0.3f,
            std::sin(phaseDist(rng)) * config.coreSpread * 0.2f,
            std::sin(angle) * config.coreSpread * 0.3f
        );
        orb.targetPosition = orb.localPosition;
        orb.velocity = Vector3D::zero();
        orb.radius = config.coreRadius;
        orb.phase = phaseDist(rng);
        orb.brightness = brightDist(rng);
        orb.layer = 0;
        orbs.push_back(orb);
    }
    
    // Mid orbs - medium spread
    for (int i = 0; i < config.midOrbs; ++i) {
        EnergyOrb orb;
        float angle = (float)i / config.midOrbs * 6.28318f + 0.3f;
        float height = std::sin(phaseDist(rng)) * 0.5f;
        orb.localPosition = Vector3D(
            std::cos(angle) * config.midSpread * 0.4f,
            height * config.midSpread * 0.3f,
            std::sin(angle) * config.midSpread * 0.4f
        );
        orb.targetPosition = orb.localPosition;
        orb.velocity = Vector3D::zero();
        orb.radius = config.midRadius;
        orb.phase = phaseDist(rng);
        orb.brightness = brightDist(rng);
        orb.layer = 1;
        orbs.push_back(orb);
    }
    
    // Outer orbs - widest spread
    for (int i = 0; i < config.outerOrbs; ++i) {
        EnergyOrb orb;
        float angle = (float)i / config.outerOrbs * 6.28318f + 0.7f;
        float height = std::sin(phaseDist(rng) + i);
        orb.localPosition = Vector3D(
            std::cos(angle) * config.outerSpread * 0.5f,
            height * config.outerSpread * 0.3f,
            std::sin(angle) * config.outerSpread * 0.5f
        );
        orb.targetPosition = orb.localPosition;
        orb.velocity = Vector3D::zero();
        orb.radius = config.outerRadius;
        orb.phase = phaseDist(rng);
        orb.brightness = brightDist(rng);
        orb.layer = 2;
        orbs.push_back(orb);
    }
}

void EnergyBeingRenderer::update(float dt, const Character3D& character) {
    time += dt;
    
    Vector3D currentPos = character.getPosition();
    Vector3D currentVel = character.getVelocity();
    float speed = currentVel.length();
    
    // Smooth velocity for more organic response
    smoothedVelocity = smoothedVelocity + (currentVel - smoothedVelocity) * std::min(dt * 5.0f, 1.0f);
    
    updateOrbPositions(dt, speed, smoothedVelocity);
    
    lastSpeed = speed;
    lastPosition = currentPos;
}

void EnergyBeingRenderer::updateOrbPositions(float dt, float speed, const Vector3D& velocity) {
    // Speed factor: 0 = stationary, 1 = full speed
    float speedFactor = std::min(speed / 150.0f, 1.0f);
    float smoothSpeedFactor = smoothstep(0.0f, 1.0f, speedFactor);
    
    // Velocity direction for flow
    Vector3D velDir = velocity.lengthSquared() > 0.01f ? velocity.normalized() : Vector3D(0, 0, 1);
    
    for (size_t i = 0; i < orbs.size(); ++i) {
        EnergyOrb& orb = orbs[i];
        
        // Calculate spread based on layer and speed
        float layerSpread = 1.0f;
        float rotSpeed = config.rotationSpeed;
        
        switch (orb.layer) {
            case 0: // Core - stays tight, rotates slowly
                layerSpread = config.coreSpread * (0.2f + smoothSpeedFactor * 0.8f);
                rotSpeed *= 0.5f;
                break;
            case 1: // Mid - moderate spread
                layerSpread = config.midSpread * (0.3f + smoothSpeedFactor * 0.7f);
                rotSpeed *= 0.8f;
                break;
            case 2: // Outer - maximum spread when moving
                layerSpread = config.outerSpread * (0.2f + smoothSpeedFactor * 1.0f);
                rotSpeed *= 1.2f;
                break;
        }
        
        // Rotation around center
        float orbAngle = orb.phase + time * rotSpeed * (1.0f + smoothSpeedFactor * 0.5f);
        float orbHeight = std::sin(orbAngle * 0.7f + orb.phase) * layerSpread * 0.3f;
        
        // Base circular position
        Vector3D basePos(
            std::cos(orbAngle) * layerSpread,
            orbHeight,
            std::sin(orbAngle) * layerSpread
        );
        
        // Flow effect - orbs trail behind when moving
        if (smoothSpeedFactor > 0.1f) {
            float flowOffset = (orb.layer + 1) * config.trailLength * smoothSpeedFactor;
            basePos = basePos - velDir * flowOffset * (1.0f + std::sin(orb.phase + time * 3.0f) * 0.3f);
        }
        
        // Organic wobble
        float wobble = std::sin(time * config.flowSpeed + orb.phase) * 0.5f;
        basePos.x += wobble * (1.0f - smoothSpeedFactor * 0.5f);
        basePos.y += std::cos(time * config.flowSpeed * 0.7f + orb.phase) * 0.3f;
        basePos.z += std::sin(time * config.flowSpeed * 1.3f + orb.phase) * 0.4f;
        
        orb.targetPosition = basePos;
        
        // Smooth movement toward target
        float moveSpeed = smoothSpeedFactor > 0.3f ? config.separateSpeed : config.mergeSpeed;
        Vector3D toTarget = orb.targetPosition - orb.localPosition;
        orb.velocity = orb.velocity + toTarget * moveSpeed * dt;
        orb.velocity = orb.velocity * 0.9f; // Damping
        orb.localPosition = orb.localPosition + orb.velocity * dt;
        
        // Update brightness with pulse
        float pulse = std::sin(time * config.pulseSpeed + orb.phase) * 0.15f + 0.85f;
        orb.brightness = pulse * (0.8f + smoothSpeedFactor * 0.2f);
    }
}

void EnergyBeingRenderer::render(const Character3D& character) {
    Vector3D center = character.getPosition();
    float speed = character.getVelocity().length();
    float speedFactor = std::min(speed / 150.0f, 1.0f);
    
    // Render glow first (background)
    renderGlow(center, speedFactor);
    
    // Render connections between nearby orbs
    renderConnections(center, speedFactor);
    
    // Render orbs back to front (outer first, core last)
    for (int layer = 2; layer >= 0; --layer) {
        for (const auto& orb : orbs) {
            if (orb.layer == layer) {
                renderOrb(orb, center, speedFactor);
            }
        }
    }
}

void EnergyBeingRenderer::renderOrb(const EnergyOrb& orb, const Vector3D& center, float speedFactor) {
    Vector3D worldPos = center + orb.localPosition;
    
    // Size pulses slightly
    float sizePulse = 1.0f + std::sin(time * 3.0f + orb.phase) * 0.15f;
    float radius = orb.radius * sizePulse * orb.brightness * 1.3f;  // Larger overall
    
    // Bright glowing colors for visibility against dark
    Color baseColor;
    switch (orb.layer) {
        case 0: baseColor = {255, 255, 255, 255}; break;      // Pure white core
        case 1: baseColor = {255, 240, 200, 240}; break;      // Warm gold mid
        default: baseColor = {255, 220, 150, 200}; break;     // Amber outer
    }
    
    float brightness = orb.brightness;
    
    // === STRONG MULTI-LAYER GLOW ===
    // Outermost bloom (very large, subtle)
    DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 5.0f, 
               {255, 200, 100, (unsigned char)(25 * brightness)});
    
    // Outer glow
    DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 3.5f, 
               {255, 220, 150, (unsigned char)(40 * brightness)});
    
    // Mid glow
    DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 2.2f, 
               {255, 235, 180, (unsigned char)(80 * brightness)});
    
    // Inner glow
    DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 1.5f, 
               {255, 245, 210, (unsigned char)(140 * brightness)});
    
    // Core
    DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius, 
               {baseColor.r, baseColor.g, baseColor.b, baseColor.a});
    
    // Bright hot center
    if (orb.layer == 0) {
        DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 0.6f, 
                   {255, 255, 255, 255});
        DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 0.3f, 
                   {255, 255, 255, 255});
    } else if (orb.layer == 1) {
        DrawSphere({worldPos.x, worldPos.y, worldPos.z}, radius * 0.5f, 
                   {255, 255, 245, 230});
    }
}

void EnergyBeingRenderer::renderGlow(const Vector3D& center, float speedFactor) {
    // Strong ambient glow - very visible in dark
    float glowPulse = 1.0f + std::sin(time * 1.5f) * 0.15f;
    float glowSize = 18.0f * glowPulse * (1.0f + speedFactor * 0.4f);
    
    // === MASSIVE MULTI-LAYER GLOW FOR VISIBILITY ===
    // Outermost atmospheric glow
    DrawSphere({center.x, center.y, center.z}, glowSize * 4.0f, 
               {255, 180, 80, 10});
    DrawSphere({center.x, center.y, center.z}, glowSize * 3.0f, 
               {255, 200, 100, 18});
    DrawSphere({center.x, center.y, center.z}, glowSize * 2.2f, 
               {255, 215, 130, 30});
    DrawSphere({center.x, center.y, center.z}, glowSize * 1.6f, 
               {255, 230, 160, 45});
    DrawSphere({center.x, center.y, center.z}, glowSize * 1.2f, 
               {255, 240, 190, 60});
    DrawSphere({center.x, center.y, center.z}, glowSize * 0.9f, 
               {255, 250, 220, 80});
    
    // Speed-based energy burst
    if (speedFactor > 0.3f) {
        float burstIntensity = (speedFactor - 0.3f) / 0.7f;
        float burstAlpha = burstIntensity * 50.0f;
        DrawSphere({center.x, center.y, center.z}, glowSize * 2.5f, 
                   {255, 220, 150, (unsigned char)burstAlpha});
        DrawSphere({center.x, center.y, center.z}, glowSize * 3.5f, 
                   {255, 200, 100, (unsigned char)(burstAlpha * 0.4f)});
    }
}

void EnergyBeingRenderer::renderConnections(const Vector3D& center, float speedFactor) {
    // When merged (low speed), draw soft connections between close orbs
    if (speedFactor > 0.7f) return; // Skip when moving fast
    
    float connectionStrength = 1.0f - speedFactor * 1.3f;
    connectionStrength = std::max(0.0f, connectionStrength);
    
    for (size_t i = 0; i < orbs.size(); ++i) {
        for (size_t j = i + 1; j < orbs.size(); ++j) {
            const EnergyOrb& a = orbs[i];
            const EnergyOrb& b = orbs[j];
            
            // Only connect orbs that are close
            float dist = (a.localPosition - b.localPosition).length();
            float maxDist = (a.radius + b.radius) * 5.0f;
            
            if (dist < maxDist) {
                float proximity = 1.0f - dist / maxDist;
                float alpha = proximity * connectionStrength * 120.0f;  // Brighter connections
                if (alpha < 8.0f) continue;
                
                Vector3D worldA = center + a.localPosition;
                Vector3D worldB = center + b.localPosition;
                Vector3D mid = (worldA + worldB) * 0.5f;
                
                // Multiple interpolation points for smooth connection
                for (float t = 0.25f; t <= 0.75f; t += 0.25f) {
                    Vector3D point = worldA + (worldB - worldA) * t;
                    float pointSize = (a.radius + b.radius) * 0.4f * proximity * (1.0f - std::abs(t - 0.5f) * 1.5f);
                    
                    DrawSphere({point.x, point.y, point.z}, pointSize * 2.5f, 
                              {255, 220, 150, (unsigned char)(alpha * 0.3f)});
                    DrawSphere({point.x, point.y, point.z}, pointSize * 1.5f, 
                              {255, 240, 200, (unsigned char)(alpha * 0.6f)});
                    DrawSphere({point.x, point.y, point.z}, pointSize, 
                              {255, 250, 230, (unsigned char)alpha});
                }
            }
        }
    }
}

float EnergyBeingRenderer::smoothstep(float edge0, float edge1, float x) {
    float t = std::clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
    return t * t * (3.0f - 2.0f * t);
}

Vector3D EnergyBeingRenderer::lerpVector(const Vector3D& a, const Vector3D& b, float t) {
    return a + (b - a) * t;
}

} // namespace ethereal
