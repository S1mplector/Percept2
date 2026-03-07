#pragma once
#include <vector>
#include <cstdint>
#include "core/Vector2D.hpp"

namespace ethereal {

class PerlinNoise {
public:
    PerlinNoise();
    explicit PerlinNoise(uint32_t seed);

    float noise(float x) const;
    float noise(float x, float y) const;
    float noise(float x, float y, float z) const;

    float octaveNoise(float x, float y, int octaves, float persistence = 0.5f) const;
    float octaveNoise(float x, float y, float z, int octaves, float persistence = 0.5f) const;

    Vector2D curl(float x, float y, float epsilon = 0.01f) const;

    void reseed(uint32_t seed);

private:
    std::vector<int> p; // Permutation table

    static float fade(float t);
    static float lerp(float t, float a, float b);
    static float grad(int hash, float x, float y, float z);
    static float grad(int hash, float x, float y);
};

} // namespace ethereal
