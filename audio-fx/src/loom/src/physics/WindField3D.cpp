#include "WindField3D.hpp"
#include <algorithm>
#include <cmath>

namespace ethereal {

WindField3D::WindField3D() : WindField3D(WindConfig3D{}) {}

WindField3D::WindField3D(const WindConfig3D& config) 
    : noise(12345)
    , noiseY(54321)
    , noiseZ(98765)
    , config(config)
    , time(0.0f) {}

void WindField3D::update(float dt) {
    time += dt * config.timeScale;

    gusts.erase(std::remove_if(gusts.begin(), gusts.end(),
        [](const Gust3D& g) { return g.elapsed >= g.duration; }), gusts.end());

    vortices.erase(std::remove_if(vortices.begin(), vortices.end(),
        [](const Vortex& v) { return v.elapsed >= v.duration; }), vortices.end());

    for (auto& gust : gusts) gust.elapsed += dt;
    for (auto& vortex : vortices) vortex.elapsed += dt;
}

Vector3D WindField3D::sampleNoise(float x, float y, float z, float t) const {
    float scale = config.noiseScale;
    float nx = noise.octaveNoise(x * scale, y * scale, z * scale + t, 3, 0.5f);
    float ny = noiseY.octaveNoise(x * scale + 100, y * scale + 100, z * scale + t + 50, 3, 0.5f);
    float nz = noiseZ.octaveNoise(x * scale + 200, y * scale + 200, z * scale + t + 100, 3, 0.5f);
    return Vector3D(nx, ny * config.verticalInfluence, nz);
}

Vector3D WindField3D::getWindAt(const Vector3D& position) const {
    return getWindAt(position.x, position.y, position.z);
}

Vector3D WindField3D::getWindAt(float x, float y, float z) const {
    Vector3D noiseVec = sampleNoise(x, y, z, time);
    
    Vector3D turbulentWind = noiseVec * config.turbulence * config.baseStrength;
    Vector3D baseWind = config.baseDirection.normalized() * config.baseStrength;
    
    float gustNoise = noise.octaveNoise(x * config.noiseScale * 0.5f, z * config.noiseScale * 0.5f, time * 0.3f, 2);
    gustNoise = std::max(0.0f, gustNoise);
    Vector3D gustWind = config.baseDirection.normalized() * gustNoise * config.gustStrength;
    
    Vector3D curlWind = getCurlAt(Vector3D(x, y, z)) * config.curlStrength;
    
    Vector3D totalWind = baseWind + turbulentWind + gustWind + curlWind;

    for (const auto& gust : gusts) {
        Vector3D toPoint = Vector3D(x, y, z) - gust.position;
        float dist = toPoint.length();
        if (dist < gust.radius) {
            float falloff = 1.0f - (dist / gust.radius);
            falloff = falloff * falloff;
            float timeRatio = gust.elapsed / gust.duration;
            float timeFalloff = std::sin(timeRatio * 3.14159f);
            totalWind += gust.direction.normalized() * gust.strength * falloff * timeFalloff;
        }
    }

    for (const auto& vortex : vortices) {
        Vector3D toPoint = Vector3D(x, y, z) - vortex.position;
        Vector3D projected = toPoint - vortex.axis * toPoint.dot(vortex.axis);
        float dist = projected.length();
        if (dist < vortex.radius && dist > 0.1f) {
            float falloff = 1.0f - (dist / vortex.radius);
            falloff = falloff * falloff;
            float timeRatio = vortex.elapsed / vortex.duration;
            float timeFalloff = std::sin(timeRatio * 3.14159f);
            
            Vector3D tangent = vortex.axis.cross(projected).normalized();
            totalWind += tangent * vortex.strength * falloff * timeFalloff;
        }
    }

    return totalWind;
}

Vector3D WindField3D::getCurlAt(const Vector3D& position, float epsilon) const {
    float px = position.x, py = position.y, pz = position.z;
    
    Vector3D dFdx = (sampleNoise(px + epsilon, py, pz, time) - sampleNoise(px - epsilon, py, pz, time)) / (2.0f * epsilon);
    Vector3D dFdy = (sampleNoise(px, py + epsilon, pz, time) - sampleNoise(px, py - epsilon, pz, time)) / (2.0f * epsilon);
    Vector3D dFdz = (sampleNoise(px, py, pz + epsilon, time) - sampleNoise(px, py, pz - epsilon, time)) / (2.0f * epsilon);
    
    return Vector3D(
        dFdy.z - dFdz.y,
        dFdz.x - dFdx.z,
        dFdx.y - dFdy.x
    );
}

float WindField3D::getStrengthAt(const Vector3D& position) const {
    return getWindAt(position).length();
}

float WindField3D::getTurbulenceAt(const Vector3D& position) const {
    return sampleNoise(position.x, position.y, position.z, time).length();
}

void WindField3D::setConfig(const WindConfig3D& newConfig) {
    config = newConfig;
}

void WindField3D::addGust(const Vector3D& position, const Vector3D& direction, float strength, float radius, float duration) {
    gusts.push_back({position, direction, strength, radius, duration, 0.0f});
}

void WindField3D::addVortex(const Vector3D& position, const Vector3D& axis, float strength, float radius, float duration) {
    vortices.push_back({position, axis.normalized(), strength, radius, duration, 0.0f});
}

} // namespace ethereal
