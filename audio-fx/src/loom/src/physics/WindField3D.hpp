#pragma once
#include "core/Vector3D.hpp"
#include "utils/PerlinNoise.hpp"

namespace ethereal {

struct WindConfig3D {
    float baseStrength = 60.0f;
    float gustStrength = 100.0f;
    float turbulence = 0.4f;
    float noiseScale = 0.006f;
    float timeScale = 0.4f;
    Vector3D baseDirection = Vector3D(1.0f, 0.0f, 0.2f);
    float verticalInfluence = 0.3f;
    float curlStrength = 0.5f;
};

class WindField3D {
public:
    WindField3D();
    explicit WindField3D(const WindConfig3D& config);

    void update(float dt);
    
    Vector3D getWindAt(const Vector3D& position) const;
    Vector3D getWindAt(float x, float y, float z) const;
    Vector3D getCurlAt(const Vector3D& position, float epsilon = 0.5f) const;
    
    float getStrengthAt(const Vector3D& position) const;
    float getTurbulenceAt(const Vector3D& position) const;
    
    void setConfig(const WindConfig3D& config);
    const WindConfig3D& getConfig() const { return config; }
    
    void addGust(const Vector3D& position, const Vector3D& direction, float strength, float radius, float duration);
    void addVortex(const Vector3D& position, const Vector3D& axis, float strength, float radius, float duration);

    float getTime() const { return time; }

private:
    PerlinNoise noise;
    PerlinNoise noiseY;
    PerlinNoise noiseZ;
    WindConfig3D config;
    float time;

    struct Gust3D {
        Vector3D position;
        Vector3D direction;
        float strength;
        float radius;
        float duration;
        float elapsed;
    };
    
    struct Vortex {
        Vector3D position;
        Vector3D axis;
        float strength;
        float radius;
        float duration;
        float elapsed;
    };
    
    std::vector<Gust3D> gusts;
    std::vector<Vortex> vortices;

    Vector3D sampleNoise(float x, float y, float z, float t) const;
};

} // namespace ethereal
