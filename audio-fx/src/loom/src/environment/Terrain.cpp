#include "Terrain.hpp"
#include <cmath>
#include <algorithm>

namespace ethereal {

Terrain::Terrain() : Terrain(TerrainConfig{}) {}

Terrain::Terrain(const TerrainConfig& config)
    : config(config)
    , mountainNoise(42)
    , duneNoise(123)
    , detailNoise(456) {}

void Terrain::generate(uint32_t seed) {
    mountainNoise.reseed(seed);
    duneNoise.reseed(seed + 1000);
    detailNoise.reseed(seed + 2000);
    
    vertices.clear();
    indices.clear();
    
    int gridSize = config.gridSize;
    float tileSize = config.tileSize;
    float halfSize = (gridSize * tileSize) * 0.5f;
    
    vertices.reserve((gridSize + 1) * (gridSize + 1));
    
    for (int z = 0; z <= gridSize; ++z) {
        for (int x = 0; x <= gridSize; ++x) {
            float worldX = x * tileSize - halfSize;
            float worldZ = z * tileSize - halfSize;
            
            float height = getHeightAt(worldX, worldZ);
            
            TerrainVertex vertex;
            vertex.position = Vector3D(worldX, height, worldZ);
            vertex.height = height;
            vertex.normal = Vector3D(0, 1, 0);
            
            vertices.push_back(vertex);
        }
    }
    
    calculateNormals();
    
    indices.reserve(gridSize * gridSize * 6);
    
    for (int z = 0; z < gridSize; ++z) {
        for (int x = 0; x < gridSize; ++x) {
            unsigned int topLeft = z * (gridSize + 1) + x;
            unsigned int topRight = topLeft + 1;
            unsigned int bottomLeft = (z + 1) * (gridSize + 1) + x;
            unsigned int bottomRight = bottomLeft + 1;
            
            indices.push_back(topLeft);
            indices.push_back(bottomLeft);
            indices.push_back(topRight);
            
            indices.push_back(topRight);
            indices.push_back(bottomLeft);
            indices.push_back(bottomRight);
        }
    }
    
    generateMountainPeaks();
}

void Terrain::generateChunk(int chunkX, int chunkZ) {
    // For streaming terrain - generates a specific chunk
    // Can be expanded for infinite terrain
}

float Terrain::sampleMountainHeight(float x, float z) const {
    float n = mountainNoise.octaveNoise(
        x * config.mountainFrequency,
        z * config.mountainFrequency,
        config.mountainOctaves,
        0.5f
    );
    
    n = (n + 1.0f) * 0.5f;
    n = std::pow(n, config.mountainPower);
    
    float ridgeNoise = mountainNoise.octaveNoise(
        x * config.mountainFrequency * 2.0f + 500,
        z * config.mountainFrequency * 2.0f + 500,
        3,
        0.6f
    );
    ridgeNoise = 1.0f - std::abs(ridgeNoise);
    ridgeNoise = std::pow(ridgeNoise, 2.0f);
    
    n = n * 0.7f + ridgeNoise * 0.3f * n;
    
    return n * config.maxHeight;
}

float Terrain::sampleDuneHeight(float x, float z) const {
    float primary = duneNoise.octaveNoise(
        x * config.duneFrequency,
        z * config.duneFrequency * 0.5f,
        config.duneOctaves,
        0.5f
    );
    
    float secondary = duneNoise.octaveNoise(
        x * config.duneFrequency * 0.7f + 200,
        z * config.duneFrequency * 1.2f + 200,
        2,
        0.4f
    );
    
    float detail = detailNoise.octaveNoise(
        x * config.duneFrequency * 3.0f,
        z * config.duneFrequency * 3.0f,
        2,
        0.3f
    );
    
    float dune = primary * 0.6f + secondary * 0.3f + detail * 0.1f;
    dune = (dune + 1.0f) * 0.5f;
    
    float windward = std::sin(x * 0.01f + z * 0.005f);
    dune *= (0.8f + windward * 0.2f);
    
    return dune * config.duneAmplitude;
}

float Terrain::getHeightAt(float x, float z) const {
    float mountainHeight = sampleMountainHeight(x, z);
    float duneHeight = sampleDuneHeight(x, z);
    
    float mountainFactor = mountainHeight / config.maxHeight;
    float blendedHeight = mountainHeight + duneHeight * (1.0f - mountainFactor * 0.8f);
    
    return config.baseHeight + blendedHeight;
}

Vector3D Terrain::getNormalAt(float x, float z) const {
    float epsilon = config.tileSize * 0.5f;
    
    float hL = getHeightAt(x - epsilon, z);
    float hR = getHeightAt(x + epsilon, z);
    float hD = getHeightAt(x, z - epsilon);
    float hU = getHeightAt(x, z + epsilon);
    
    Vector3D normal(hL - hR, 2.0f * epsilon, hD - hU);
    return normal.normalized();
}

Color Terrain::getColorAt(float x, float z, float height) const {
    float normalizedHeight = (height - config.baseHeight) / config.maxHeight;
    normalizedHeight = std::clamp(normalizedHeight, 0.0f, 1.0f);
    
    Vector3D normal = getNormalAt(x, z);
    float steepness = 1.0f - normal.y;
    
    Color baseColor;
    
    if (normalizedHeight > config.peakThreshold) {
        float t = (normalizedHeight - config.peakThreshold) / (1.0f - config.peakThreshold);
        baseColor = {
            (unsigned char)(config.rockColorLight.r + (config.peakColor.r - config.rockColorLight.r) * t),
            (unsigned char)(config.rockColorLight.g + (config.peakColor.g - config.rockColorLight.g) * t),
            (unsigned char)(config.rockColorLight.b + (config.peakColor.b - config.rockColorLight.b) * t),
            255
        };
    }
    else if (normalizedHeight > config.rockThreshold || steepness > 0.4f) {
        float rockBlend = std::max(
            (normalizedHeight - config.rockThreshold * 0.8f) / (config.peakThreshold - config.rockThreshold * 0.8f),
            steepness * 1.5f
        );
        rockBlend = std::clamp(rockBlend, 0.0f, 1.0f);
        
        baseColor = {
            (unsigned char)(config.sandColorDark.r + (config.rockColorLight.r - config.sandColorDark.r) * rockBlend),
            (unsigned char)(config.sandColorDark.g + (config.rockColorLight.g - config.sandColorDark.g) * rockBlend),
            (unsigned char)(config.sandColorDark.b + (config.rockColorLight.b - config.sandColorDark.b) * rockBlend),
            255
        };
    }
    else {
        float duneVariation = detailNoise.noise(x * 0.05f, z * 0.05f);
        duneVariation = (duneVariation + 1.0f) * 0.5f;
        
        baseColor = {
            (unsigned char)(config.sandColorDark.r + (config.sandColorLight.r - config.sandColorDark.r) * duneVariation),
            (unsigned char)(config.sandColorDark.g + (config.sandColorLight.g - config.sandColorDark.g) * duneVariation),
            (unsigned char)(config.sandColorDark.b + (config.sandColorLight.b - config.sandColorDark.b) * duneVariation),
            255
        };
    }
    
    float shadowFactor = 0.7f + normal.y * 0.3f;
    baseColor.r = static_cast<unsigned char>(baseColor.r * shadowFactor);
    baseColor.g = static_cast<unsigned char>(baseColor.g * shadowFactor);
    baseColor.b = static_cast<unsigned char>(baseColor.b * shadowFactor);
    
    return baseColor;
}

void Terrain::calculateNormals() {
    int gridSize = config.gridSize;
    
    for (size_t i = 0; i < vertices.size(); ++i) {
        vertices[i].normal = Vector3D(0, 0, 0);
    }
    
    for (int z = 0; z < gridSize; ++z) {
        for (int x = 0; x < gridSize; ++x) {
            int idx0 = z * (gridSize + 1) + x;
            int idx1 = idx0 + 1;
            int idx2 = (z + 1) * (gridSize + 1) + x;
            int idx3 = idx2 + 1;
            
            Vector3D v0 = vertices[idx0].position;
            Vector3D v1 = vertices[idx1].position;
            Vector3D v2 = vertices[idx2].position;
            Vector3D v3 = vertices[idx3].position;
            
            Vector3D n1 = (v2 - v0).cross(v1 - v0).normalized();
            Vector3D n2 = (v1 - v3).cross(v2 - v3).normalized();
            
            vertices[idx0].normal += n1;
            vertices[idx1].normal += n1 + n2;
            vertices[idx2].normal += n1 + n2;
            vertices[idx3].normal += n2;
        }
    }
    
    for (auto& vertex : vertices) {
        vertex.normal = vertex.normal.normalized();
    }
}

void Terrain::generateMountainPeaks() {
    mountains.clear();
    
    float totalSize = getTotalSize();
    float halfSize = totalSize * 0.5f;
    float searchStep = totalSize / 20.0f;
    
    for (float z = -halfSize; z < halfSize; z += searchStep) {
        for (float x = -halfSize; x < halfSize; x += searchStep) {
            float h = getHeightAt(x, z);
            float normalizedH = (h - config.baseHeight) / config.maxHeight;
            
            if (normalizedH > 0.6f) {
                bool isLocalMax = true;
                float checkRadius = searchStep * 0.8f;
                
                for (float dz = -checkRadius; dz <= checkRadius && isLocalMax; dz += checkRadius) {
                    for (float dx = -checkRadius; dx <= checkRadius && isLocalMax; dx += checkRadius) {
                        if (dx == 0 && dz == 0) continue;
                        if (getHeightAt(x + dx, z + dz) > h) {
                            isLocalMax = false;
                        }
                    }
                }
                
                if (isLocalMax) {
                    Mountain peak;
                    peak.position = Vector3D(x, h, z);
                    peak.height = h;
                    peak.radius = searchStep * (0.5f + normalizedH);
                    peak.steepness = normalizedH;
                    mountains.push_back(peak);
                }
            }
        }
    }
}

void Terrain::setConfig(const TerrainConfig& cfg) {
    config = cfg;
}

} // namespace ethereal
