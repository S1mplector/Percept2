#include "WindField.hpp"
#include <algorithm>
#include <cmath>

namespace ethereal {

WindField::WindField() : WindField(WindConfig{}) {}

WindField::WindField(const WindConfig& config) 
    : noise(12345)
    , config(config)
    , time(0.0f) {}

void WindField::update(float dt) {
    time += dt * config.timeScale;

    gusts.erase(std::remove_if(gusts.begin(), gusts.end(),
        [](const Gust& g) { return g.elapsed >= g.duration; }), gusts.end());

    for (auto& gust : gusts) {
        gust.elapsed += dt;
    }
}

Vector2D WindField::sampleNoise(float x, float y, float t) const {
    float nx = noise.octaveNoise(x * config.noiseScale, y * config.noiseScale, t, 3, 0.5f);
    float ny = noise.octaveNoise(x * config.noiseScale + 100.0f, y * config.noiseScale + 100.0f, t + 50.0f, 3, 0.5f);
    
    return Vector2D(nx, ny);
}

Vector2D WindField::getWindAt(const Vector2D& position) const {
    return getWindAt(position.x, position.y);
}

Vector2D WindField::getWindAt(float x, float y) const {
    Vector2D noiseVec = sampleNoise(x, y, time);
    
    Vector2D turbulentWind = noiseVec * config.turbulence;
    Vector2D baseWind = config.baseDirection.normalized() * config.baseStrength;
    
    float gustNoise = noise.octaveNoise(x * config.noiseScale * 0.5f, y * config.noiseScale * 0.5f, time * 0.3f, 2);
    gustNoise = std::max(0.0f, gustNoise);
    Vector2D gustWind = config.baseDirection.normalized() * gustNoise * config.gustStrength;
    
    Vector2D totalWind = baseWind + turbulentWind * config.baseStrength + gustWind;

    for (const auto& gust : gusts) {
        Vector2D toPoint = Vector2D(x, y) - gust.position;
        float dist = toPoint.length();
        if (dist < gust.radius) {
            float falloff = 1.0f - (dist / gust.radius);
            falloff = falloff * falloff;
            
            float timeRatio = gust.elapsed / gust.duration;
            float timeFalloff = std::sin(timeRatio * 3.14159f);
            
            totalWind += toPoint.normalized() * gust.strength * falloff * timeFalloff;
        }
    }

    return totalWind;
}

float WindField::getStrengthAt(const Vector2D& position) const {
    return getWindAt(position).length();
}

void WindField::setConfig(const WindConfig& newConfig) {
    config = newConfig;
}

const WindConfig& WindField::getConfig() const {
    return config;
}

void WindField::addGust(const Vector2D& position, float strength, float radius, float duration) {
    gusts.push_back({position, strength, radius, duration, 0.0f});
}

} // namespace ethereal
