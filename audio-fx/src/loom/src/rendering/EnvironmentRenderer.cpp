#include "EnvironmentRenderer.hpp"
#include "rlgl.h"
#include <algorithm>
#include <cmath>

namespace ethereal {

EnvironmentRenderer::EnvironmentRenderer() : EnvironmentRenderer(EnvironmentConfig{}) {}

EnvironmentRenderer::EnvironmentRenderer(const EnvironmentConfig& cfg)
    : config(cfg)
    , time(0.0f) {
    particles.reserve(cfg.atmosphereParticles);
}

void EnvironmentRenderer::initialize() {
    initParticles(Vector3D::zero());
    initStars();
}

void EnvironmentRenderer::initStars() {
    starPositions.clear();
    starBrightnesses.clear();
    
    for (int i = 0; i < config.starCount; ++i) {
        // Random position on a sphere
        float theta = GetRandomValue(0, 628) * 0.01f;
        float phi = std::acos(1.0f - 2.0f * GetRandomValue(0, 1000) / 1000.0f);
        
        Vector3D pos(
            std::sin(phi) * std::cos(theta),
            std::cos(phi),
            std::sin(phi) * std::sin(theta)
        );
        
        // Only keep stars in upper hemisphere
        if (pos.y > -0.1f) {
            starPositions.push_back(pos * 800.0f);  // Far distance
            starBrightnesses.push_back(0.3f + GetRandomValue(0, 70) / 100.0f);
        }
    }
}

void EnvironmentRenderer::initParticles(const Vector3D& center) {
    particles.clear();
    for (int i = 0; i < config.atmosphereParticles; ++i) {
        AtmosphereParticle3D p;
        float angle = GetRandomValue(0, 628) * 0.01f;
        float dist = GetRandomValue(50, (int)config.particleSpawnRadius);
        p.position = center + Vector3D(
            std::cos(angle) * dist,
            GetRandomValue(-30, 200),
            std::sin(angle) * dist
        );
        p.velocity = Vector3D::zero();
        p.size = GetRandomValue(5, 18) / 10.0f;
        p.alpha = GetRandomValue(15, 45) / 100.0f;
        p.lifetime = GetRandomValue(0, 1000) / 100.0f;
        p.type = GetRandomValue(0, 2);
        particles.push_back(p);
    }
}

void EnvironmentRenderer::update(float dt, const Vector3D& cameraPos, const WindField3D& wind) {
    time += dt;
    updateParticles(dt, cameraPos, wind);
}

void EnvironmentRenderer::updateParticles(float dt, const Vector3D& cameraPos, const WindField3D& wind) {
    for (auto& p : particles) {
        // Wind influence
        Vector3D windForce = wind.getWindAt(p.position) * 0.002f;
        
        // Gentle floating motion
        float swirl = std::sin(p.lifetime * 1.5f + p.position.x * 0.01f) * 0.4f;
        Vector3D drift(swirl, 0.2f + std::sin(p.lifetime) * 0.1f, std::cos(p.lifetime * 1.2f) * 0.3f);
        
        p.velocity = p.velocity * 0.95f + windForce + drift * dt;
        p.position = p.position + p.velocity * dt * 30.0f;
        p.lifetime += dt;
        
        // Fade based on lifetime
        p.alpha = std::max(0.0f, 0.35f - p.lifetime * 0.015f);
        
        // Respawn if too far or faded
        float dist = (p.position - cameraPos).length();
        if (dist > config.particleSpawnRadius * 1.2f || p.position.y < -60.0f || p.alpha <= 0.0f) {
            float angle = GetRandomValue(0, 628) * 0.01f;
            float spawnDist = GetRandomValue(80, (int)(config.particleSpawnRadius * 0.8f));
            p.position = cameraPos + Vector3D(
                std::cos(angle) * spawnDist,
                GetRandomValue(-20, 180),
                std::sin(angle) * spawnDist
            );
            p.velocity = Vector3D::zero();
            p.lifetime = 0;
            p.alpha = 0.25f + GetRandomValue(0, 15) * 0.01f;
            p.size = 0.6f + GetRandomValue(0, 12) * 0.1f;
        }
    }
}

void EnvironmentRenderer::renderSky(const FlightCamera& camera, float gameTime) {
    int screenWidth = GetScreenWidth();
    int screenHeight = GetScreenHeight();
    
    // Dark night sky gradient
    for (int y = 0; y < screenHeight; ++y) {
        float t = static_cast<float>(y) / screenHeight;
        t = t * t * (3.0f - 2.0f * t);
        Color lineColor = blendColors(config.skyColorZenith, config.skyColorHorizon, t);
        DrawLine(0, y, screenWidth, y, lineColor);
    }
}

void EnvironmentRenderer::renderMoonAndStars(const FlightCamera& camera, float gameTime) {
    Vector3D camPos = camera.getPosition();
    
    BeginMode3D({
        {camPos.x, camPos.y, camPos.z},
        {camera.getTarget().x, camera.getTarget().y, camera.getTarget().z},
        {0, 1, 0}, 65.0f, CAMERA_PERSPECTIVE
    });
    
    rlDisableDepthMask();
    rlDisableDepthTest();
    
    // === STARS ===
    for (size_t i = 0; i < starPositions.size(); ++i) {
        Vector3D starPos = camPos + starPositions[i];
        float brightness = starBrightnesses[i];
        
        // Twinkling effect
        float twinkle = std::sin(gameTime * (2.0f + i * 0.1f) + i * 0.5f);
        twinkle = twinkle * 0.3f + 0.7f;
        brightness *= twinkle * config.starBrightness;
        
        unsigned char alpha = (unsigned char)(brightness * 255);
        float size = 0.8f + brightness * 1.2f;
        
        // Star core
        DrawSphere({starPos.x, starPos.y, starPos.z}, size, {255, 255, 255, alpha});
        // Subtle glow
        DrawSphere({starPos.x, starPos.y, starPos.z}, size * 2.5f, {200, 220, 255, (unsigned char)(alpha / 5)});
    }
    
    // === MOON ===
    if (config.enableMoon) {
        Vector3D moonDir = config.moonDirection.normalized();
        Vector3D moonPos = camPos + moonDir * 700.0f;
        
        float moonPulse = 1.0f + std::sin(gameTime * 0.2f) * 0.02f;
        
        // Moon glow layers (soft ethereal glow)
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonGlowSize * 3.0f * moonPulse, {180, 200, 255, 8});
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonGlowSize * 2.0f * moonPulse, {200, 215, 255, 15});
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonGlowSize * 1.3f * moonPulse, {210, 225, 255, 30});
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonGlowSize * moonPulse, {220, 235, 255, 50});
        
        // Moon body
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonSize * 1.2f, {230, 240, 255, 180});
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonSize, config.moonColor);
        
        // Bright center
        DrawSphere({moonPos.x, moonPos.y, moonPos.z}, config.moonSize * 0.7f, {240, 245, 255, 255});
    }
    
    rlEnableDepthTest();
    rlEnableDepthMask();
    
    EndMode3D();
}

void EnvironmentRenderer::renderTerrain(const Terrain& terrain, const FlightCamera& camera) {
    BeginMode3D({
        {camera.getPosition().x, camera.getPosition().y, camera.getPosition().z},
        {camera.getTarget().x, camera.getTarget().y, camera.getTarget().z},
        {0, 1, 0}, 65.0f, CAMERA_PERSPECTIVE
    });
    
    const auto& vertices = terrain.getVertices();
    const auto& indices = terrain.getIndices();
    const auto& terrainConfig = terrain.getConfig();
    
    Vector3D camPos = camera.getPosition();
    Vector3D sunDir = config.sunDirection.normalized();
    
    for (size_t i = 0; i < indices.size(); i += 3) {
        const auto& v0 = vertices[indices[i]];
        const auto& v1 = vertices[indices[i + 1]];
        const auto& v2 = vertices[indices[i + 2]];
        
        Vector3D center = (v0.position + v1.position + v2.position) * (1.0f / 3.0f);
        float dist = (center - camPos).length();
        
        if (dist > config.terrainViewDistance) continue;
        
        // Face normal for lighting
        Vector3D edge1 = v1.position - v0.position;
        Vector3D edge2 = v2.position - v0.position;
        Vector3D faceNormal = edge1.cross(edge2).normalized();
        
        // Lighting
        float diffuse = std::max(0.0f, faceNormal.dot(sunDir));
        diffuse = 0.35f + diffuse * 0.65f;
        
        // Rim lighting
        Vector3D toCamera = (camPos - center).normalized();
        float rim = 1.0f - std::max(0.0f, faceNormal.dot(toCamera));
        rim = std::pow(rim, 2.5f) * 0.12f;
        
        // Height-based color
        float avgHeight = (v0.height + v1.height + v2.height) / 3.0f;
        float normalizedHeight = std::clamp((avgHeight - terrainConfig.baseHeight) / terrainConfig.maxHeight, 0.0f, 1.0f);
        
        Color baseColor = getTerrainColor(normalizedHeight, faceNormal.y);
        
        // Apply lighting
        float light = std::clamp(diffuse + rim, 0.0f, 1.2f);
        baseColor.r = (unsigned char)std::clamp((int)(baseColor.r * light), 0, 255);
        baseColor.g = (unsigned char)std::clamp((int)(baseColor.g * light), 0, 255);
        baseColor.b = (unsigned char)std::clamp((int)(baseColor.b * light), 0, 255);
        
        // Apply fog
        Color finalColor = applyFog(baseColor, dist, center.y);
        
        DrawTriangle3D(
            {v0.position.x, v0.position.y, v0.position.z},
            {v1.position.x, v1.position.y, v1.position.z},
            {v2.position.x, v2.position.y, v2.position.z},
            finalColor
        );
        DrawTriangle3D(
            {v0.position.x, v0.position.y, v0.position.z},
            {v2.position.x, v2.position.y, v2.position.z},
            {v1.position.x, v1.position.y, v1.position.z},
            finalColor
        );
    }
    
    EndMode3D();
}

Color EnvironmentRenderer::getTerrainColor(float height, float steepness) {
    // Dark night palette - cool blues and purples
    if (height > 0.82f) {
        // Snow peaks - slightly luminous in moonlight
        float t = (height - 0.82f) / 0.18f;
        return {
            (unsigned char)(80 + t * 40),
            (unsigned char)(90 + t * 50),
            (unsigned char)(110 + t * 50),
            255
        };
    } else if (height > 0.55f) {
        // Rocky areas - dark grey-blue
        float t = (height - 0.55f) / 0.27f;
        return {
            (unsigned char)(40 + t * 40),
            (unsigned char)(45 + t * 45),
            (unsigned char)(55 + t * 55),
            255
        };
    } else if (height > 0.3f) {
        // Mid slopes - dark purple-grey
        float t = (height - 0.3f) / 0.25f;
        return {
            (unsigned char)(35 + t * 5),
            (unsigned char)(30 + t * 15),
            (unsigned char)(40 + t * 15),
            255
        };
    } else if (height > 0.12f) {
        // Lower areas - dark earth tones
        float t = (height - 0.12f) / 0.18f;
        return {
            (unsigned char)(30 + t * 5),
            (unsigned char)(28 + t * 2),
            (unsigned char)(35 + t * 5),
            255
        };
    } else {
        // Lowest - very dark
        float t = height / 0.12f;
        return {
            (unsigned char)(20 + t * 10),
            (unsigned char)(22 + t * 6),
            (unsigned char)(28 + t * 7),
            255
        };
    }
}

void EnvironmentRenderer::renderClouds(const FlightCamera& camera, float gameTime) {
    BeginMode3D({
        {camera.getPosition().x, camera.getPosition().y, camera.getPosition().z},
        {camera.getTarget().x, camera.getTarget().y, camera.getTarget().z},
        {0, 1, 0}, 65.0f, CAMERA_PERSPECTIVE
    });
    
    Vector3D camPos = camera.getPosition();
    
    for (int layer = 0; layer < config.cloudLayers; ++layer) {
        float layerHeight = config.cloudBaseHeight + layer * config.cloudLayerSpacing;
        float layerRadius = 400.0f + layer * 100.0f;
        float layerAlpha = 0.9f - layer * 0.15f;
        
        for (int i = 0; i < config.cloudsPerLayer; ++i) {
            float angle = (float)i / config.cloudsPerLayer * 6.28318f;
            angle += gameTime * (0.006f - layer * 0.001f) + layer * 0.6f;
            
            float x = camPos.x + std::cos(angle) * layerRadius;
            float z = camPos.z + std::sin(angle) * layerRadius;
            float y = layerHeight + std::sin(angle * 1.3f + gameTime * 0.06f) * 10.0f;
            
            float baseSize = 55.0f + (i % 4) * 12.0f - layer * 6.0f;
            unsigned char alpha = (unsigned char)(28 * layerAlpha);
            
            // Soft layered cloud
            DrawSphere({x, y, z}, baseSize * 0.5f, {255, 253, 250, (unsigned char)(alpha * 1.3f)});
            DrawSphere({x, y, z}, baseSize * 0.75f, {255, 252, 248, (unsigned char)(alpha * 0.8f)});
            DrawSphere({x, y, z}, baseSize, {255, 250, 245, (unsigned char)(alpha * 0.4f)});
            
            // Puffs
            for (int p = 0; p < 3; ++p) {
                float pAngle = p * 2.094f + i * 0.8f + gameTime * 0.015f;
                float px = x + std::cos(pAngle) * baseSize * 0.5f;
                float pz = z + std::sin(pAngle) * baseSize * 0.5f;
                float py = y + std::sin(pAngle * 2.0f) * baseSize * 0.2f;
                float pSize = baseSize * (0.35f + (p % 2) * 0.1f);
                
                DrawSphere({px, py, pz}, pSize * 0.6f, {255, 252, 248, (unsigned char)(alpha * 0.9f)});
                DrawSphere({px, py, pz}, pSize, {255, 250, 245, (unsigned char)(alpha * 0.5f)});
            }
        }
    }
    
    // High wisps
    for (int i = 0; i < 10; ++i) {
        float angle = i * 0.628f + gameTime * 0.003f;
        float radius = 550.0f + (i % 3) * 70.0f;
        float x = camPos.x + std::cos(angle) * radius;
        float z = camPos.z + std::sin(angle) * radius;
        float y = 350.0f + (i % 4) * 20.0f;
        float size = 80.0f + (i % 2) * 25.0f;
        
        DrawSphere({x, y, z}, size * 0.35f, {248, 250, 255, 15});
        DrawSphere({x, y, z}, size * 0.6f, {245, 248, 255, 8});
        DrawSphere({x, y, z}, size, {242, 245, 255, 4});
    }
    
    EndMode3D();
}

void EnvironmentRenderer::renderAtmosphere(const FlightCamera& camera, float dt) {
    BeginMode3D({
        {camera.getPosition().x, camera.getPosition().y, camera.getPosition().z},
        {camera.getTarget().x, camera.getTarget().y, camera.getTarget().z},
        {0, 1, 0}, 65.0f, CAMERA_PERSPECTIVE
    });
    
    Vector3D camPos = camera.getPosition();
    
    for (size_t i = 0; i < particles.size(); ++i) {
        const auto& p = particles[i];
        
        float dist = (p.position - camPos).length();
        float distFade = 1.0f - std::pow(std::min(dist / config.particleSpawnRadius, 1.0f), 1.3f);
        
        if (distFade < 0.05f) continue;
        
        float phase = time * 2.0f + i * 0.5f;
        
        if (p.type == 0) {
            // Dust motes
            float pulse = 0.75f + 0.25f * std::sin(phase * 0.4f);
            unsigned char alpha = (unsigned char)(p.alpha * distFade * 140 * pulse);
            if (alpha > 6) {
                DrawSphere({p.position.x, p.position.y, p.position.z}, p.size * 0.7f, {255, 248, 235, alpha});
                DrawSphere({p.position.x, p.position.y, p.position.z}, p.size * 1.5f, {255, 242, 220, (unsigned char)(alpha / 4)});
            }
        } else if (p.type == 1) {
            // Sparkles
            float sparkle = std::sin(phase * 2.5f);
            sparkle = sparkle * sparkle * sparkle;
            if (sparkle > 0.25f) {
                float intensity = (sparkle - 0.25f) / 0.75f;
                unsigned char alpha = (unsigned char)(p.alpha * distFade * 200 * intensity);
                if (alpha > 12) {
                    float size = p.size * 0.4f * (0.6f + intensity * 0.4f);
                    DrawSphere({p.position.x, p.position.y, p.position.z}, size, {255, 255, 250, alpha});
                    DrawSphere({p.position.x, p.position.y, p.position.z}, size * 2.5f, {255, 235, 190, (unsigned char)(alpha / 3)});
                }
            }
        } else {
            // Wisps
            float drift = std::sin(phase * 0.25f) * 0.5f + 0.5f;
            unsigned char alpha = (unsigned char)(p.alpha * distFade * 80 * drift);
            if (alpha > 4) {
                DrawSphere({p.position.x, p.position.y, p.position.z}, p.size * 1.0f, {245, 248, 255, alpha});
                DrawSphere({p.position.x, p.position.y, p.position.z}, p.size * 2.0f, {240, 245, 255, (unsigned char)(alpha / 4)});
            }
        }
    }
    
    EndMode3D();
}

void EnvironmentRenderer::renderDistantMountains(const FlightCamera& camera, float gameTime) {
    BeginMode3D({
        {camera.getPosition().x, camera.getPosition().y, camera.getPosition().z},
        {camera.getTarget().x, camera.getTarget().y, camera.getTarget().z},
        {0, 1, 0}, 65.0f, CAMERA_PERSPECTIVE
    });
    
    Vector3D camPos = camera.getPosition();
    
    // Distant silhouette mountains
    for (int i = 0; i < 8; ++i) {
        float angle = i * 0.785f + 0.4f;
        float radius = 1500.0f + (i % 3) * 200.0f;
        float height = 150.0f + (i % 4) * 80.0f;
        
        float x = camPos.x + std::cos(angle) * radius;
        float z = camPos.z + std::sin(angle) * radius;
        float y = -20.0f;
        
        // Foggy distant peaks
        unsigned char alpha = (unsigned char)(25 - (i % 3) * 5);
        Color peakColor = blendColors({180, 190, 210, alpha}, config.fogColor, 0.6f);
        peakColor.a = alpha;
        
        // Simple pyramid shape
        float baseSize = 200.0f + (i % 2) * 100.0f;
        DrawSphere({x, y + height * 0.5f, z}, baseSize, peakColor);
    }
    
    EndMode3D();
}

Color EnvironmentRenderer::blendColors(Color a, Color b, float t) {
    return {
        (unsigned char)(a.r + (b.r - a.r) * t),
        (unsigned char)(a.g + (b.g - a.g) * t),
        (unsigned char)(a.b + (b.b - a.b) * t),
        (unsigned char)(a.a + (b.a - a.a) * t)
    };
}

Color EnvironmentRenderer::applyFog(Color color, float distance, float height) {
    float fogFactor = std::clamp((distance - config.fogStart) / (config.fogEnd - config.fogStart), 0.0f, 1.0f);
    fogFactor = fogFactor * fogFactor;
    
    // Night fog - dark blue/purple
    float heightFactor = std::clamp(height / 250.0f, 0.0f, 1.0f);
    Color fogCol = {
        (unsigned char)(15 + heightFactor * 5),
        (unsigned char)(18 + heightFactor * 7),
        (unsigned char)(30 + heightFactor * 10),
        255
    };
    
    return blendColors(color, fogCol, fogFactor * 0.9f);
}

float EnvironmentRenderer::calculateLighting(const Vector3D& normal) {
    Vector3D sunDir = config.sunDirection.normalized();
    float diffuse = std::max(0.0f, normal.dot(sunDir));
    return 0.35f + diffuse * 0.65f;
}

} // namespace ethereal
