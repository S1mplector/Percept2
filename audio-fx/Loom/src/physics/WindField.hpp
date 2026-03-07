#pragma once
#include "core/Vector2D.hpp"
#include "utils/PerlinNoise.hpp"

namespace ethereal {

struct WindConfig {
    float baseStrength = 50.0f;
    float gustStrength = 80.0f;
    float turbulence = 0.3f;
    float noiseScale = 0.008f;
    float timeScale = 0.5f;
    Vector2D baseDirection = Vector2D(1.0f, 0.2f);
};

class WindField {
public:
    WindField();
    explicit WindField(const WindConfig& config);

    void update(float dt);
    Vector2D getWindAt(const Vector2D& position) const;
    Vector2D getWindAt(float x, float y) const;
    
    float getStrengthAt(const Vector2D& position) const;
    
    void setConfig(const WindConfig& config);
    const WindConfig& getConfig() const;
    
    void addGust(const Vector2D& position, float strength, float radius, float duration);

    float getTime() const { return time; }

private:
    PerlinNoise noise;
    WindConfig config;
    float time;

    struct Gust {
        Vector2D position;
        float strength;
        float radius;
        float duration;
        float elapsed;
    };
    std::vector<Gust> gusts;

    Vector2D sampleNoise(float x, float y, float t) const;
};

} // namespace ethereal
