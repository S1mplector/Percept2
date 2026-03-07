#pragma once
#include "raylib.h"
#include "core/Vector3D.hpp"
#include "utils/PerlinNoise.hpp"
#include <vector>

namespace ethereal {

struct TerrainVertex {
    Vector3D position;
    Vector3D normal;
    float height;
};

struct TerrainConfig {
    int gridSize = 64;
    float tileSize = 20.0f;
    float maxHeight = 250.0f;
    float mountainFrequency = 0.008f;
    float duneFrequency = 0.025f;
    float mountainPower = 2.5f;
    float duneAmplitude = 15.0f;
    int mountainOctaves = 5;
    int duneOctaves = 3;
    float baseHeight = -50.0f;
    
    // Colors
    Color sandColorLight = {235, 220, 180, 255};
    Color sandColorDark = {200, 180, 140, 255};
    Color rockColorLight = {160, 145, 130, 255};
    Color rockColorDark = {100, 90, 80, 255};
    Color peakColor = {240, 235, 230, 255};
    
    float rockThreshold = 0.5f;
    float peakThreshold = 0.85f;
};

class Terrain {
public:
    Terrain();
    explicit Terrain(const TerrainConfig& config);

    void generate(uint32_t seed = 42);
    void generateChunk(int chunkX, int chunkZ);
    
    float getHeightAt(float x, float z) const;
    Vector3D getNormalAt(float x, float z) const;
    Color getColorAt(float x, float z, float height) const;
    
    const std::vector<TerrainVertex>& getVertices() const { return vertices; }
    const std::vector<unsigned int>& getIndices() const { return indices; }
    
    void setConfig(const TerrainConfig& cfg);
    const TerrainConfig& getConfig() const { return config; }
    
    float getTotalSize() const { return config.gridSize * config.tileSize; }
    
    struct Mountain {
        Vector3D position;
        float height;
        float radius;
        float steepness;
    };
    
    const std::vector<Mountain>& getMountains() const { return mountains; }

private:
    TerrainConfig config;
    PerlinNoise mountainNoise;
    PerlinNoise duneNoise;
    PerlinNoise detailNoise;
    
    std::vector<TerrainVertex> vertices;
    std::vector<unsigned int> indices;
    std::vector<Mountain> mountains;
    
    float sampleMountainHeight(float x, float z) const;
    float sampleDuneHeight(float x, float z) const;
    void calculateNormals();
    void generateMountainPeaks();
};

} // namespace ethereal
