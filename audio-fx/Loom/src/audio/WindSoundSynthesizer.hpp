#pragma once
#include "raylib.h"
#include "core/Vector3D.hpp"
#include <cmath>
#include <random>
#include <vector>
#include <array>

namespace ethereal {

struct WindSoundConfig {
    float masterVolume = 0.7f;
    float sampleRate = 44100.0f;
    
    // Layer volumes
    float lowWindVolume = 0.5f;      // Deep rumble layer
    float midWindVolume = 0.6f;      // Main woosh layer
    float highWindVolume = 0.4f;     // Whistle/high frequency layer
    float gustVolume = 0.5f;         // Gust overlay layer
    
    // Filter frequencies (Hz)
    float lowPassBase = 200.0f;      // Low rumble cutoff
    float midLowPass = 800.0f;       // Mid woosh low cutoff
    float midHighPass = 150.0f;      // Mid woosh high cutoff
    float highPassBase = 2000.0f;    // High whistle cutoff
    
    // Modulation rates
    float slowLfoRate = 0.15f;       // Slow breathing modulation
    float mediumLfoRate = 0.6f;      // Medium variation
    float fastLfoRate = 2.5f;        // Fast flutter
    float gustRate = 0.08f;          // Gust occurrence rate
    
    // Dynamic response
    float speedInfluence = 0.8f;     // How much player speed affects sound
    float windInfluence = 0.6f;      // How much wind field affects sound
    float altitudeInfluence = 0.3f;  // How altitude affects sound
};

// Simple biquad filter for shaping noise
class BiquadFilter {
public:
    enum class Type { LowPass, HighPass, BandPass };
    
    BiquadFilter() : x1(0), x2(0), y1(0), y2(0) {}
    
    void setCoefficients(Type type, float frequency, float q, float sampleRate);
    float process(float input);
    void reset();
    
private:
    float b0, b1, b2, a1, a2;
    float x1, x2, y1, y2;
};

// Low-frequency oscillator for natural modulation
class LFO {
public:
    LFO(float rate = 1.0f, float phase = 0.0f) 
        : rate(rate), phase(phase), sampleRate(44100.0f) {}
    
    void setRate(float r) { rate = r; }
    void setSampleRate(float sr) { sampleRate = sr; }
    
    float sine();           // Smooth sine wave
    float triangle();       // Triangle wave
    float smoothRandom();   // Smoothed random (Perlin-like)
    void advance();
    
private:
    float rate;
    float phase;
    float sampleRate;
    float smoothValue = 0.0f;
    float targetValue = 0.0f;
    std::mt19937 rng{std::random_device{}()};
};

// Noise generator with different characteristics
class NoiseGenerator {
public:
    NoiseGenerator();
    
    float white();          // Pure white noise
    float pink();           // Pink noise (1/f)
    float brown();          // Brown/red noise (1/f²)
    float filtered(float cutoff, float resonance);  // Filtered noise
    
    void reset();
    
private:
    std::mt19937 rng;
    std::uniform_real_distribution<float> dist{-1.0f, 1.0f};
    
    // Pink noise state (Voss-McCartney algorithm)
    std::array<float, 16> pinkRows{};
    int pinkIndex = 0;
    float pinkRunningSum = 0.0f;
    
    // Brown noise state
    float brownValue = 0.0f;
    
    // Simple filter state
    float filterState = 0.0f;
};

// Wind gust generator for natural-sounding gusts
class GustGenerator {
public:
    GustGenerator(float sampleRate = 44100.0f);
    
    float generate();
    void trigger(float intensity = 1.0f);
    void update();
    bool isActive() const { return active; }
    
    void setSampleRate(float sr) { sampleRate = sr; }
    
private:
    float sampleRate;
    bool active = false;
    float phase = 0.0f;
    float duration = 0.0f;
    float intensity = 0.0f;
    float attackTime = 0.0f;
    float decayTime = 0.0f;
    
    std::mt19937 rng{std::random_device{}()};
};

class WindSoundSynthesizer {
public:
    WindSoundSynthesizer();
    explicit WindSoundSynthesizer(const WindSoundConfig& config);
    ~WindSoundSynthesizer();
    
    void initialize();
    void shutdown();
    
    // Main update - call each frame with game state
    void update(float dt, float playerSpeed, float windIntensity, float altitude);
    
    // Set intensity directly (0-1)
    void setIntensity(float intensity);
    
    // Trigger a gust manually
    void triggerGust(float intensity = 1.0f);
    
    // Volume control
    void setMasterVolume(float volume);
    float getMasterVolume() const { return config.masterVolume; }
    
    // Enable/disable
    void setEnabled(bool enabled);
    bool isEnabled() const { return enabled; }
    
    // Access config
    const WindSoundConfig& getConfig() const { return config; }
    void setConfig(const WindSoundConfig& cfg);

private:
    WindSoundConfig config;
    bool initialized = false;
    bool enabled = true;
    
    AudioStream stream;
    std::vector<float> buffer;
    static constexpr int BUFFER_SIZE = 4096;
    
    // Current state
    float currentIntensity = 0.0f;
    float targetIntensity = 0.0f;
    float playerSpeedNorm = 0.0f;
    float windIntensityNorm = 0.0f;
    float altitudeNorm = 0.0f;
    
    // Synthesis components
    NoiseGenerator noiseGenLow;
    NoiseGenerator noiseGenMid;
    NoiseGenerator noiseGenHigh;
    NoiseGenerator noiseGenGust;
    
    BiquadFilter lowPassLow;
    BiquadFilter lowPassMid;
    BiquadFilter highPassMid;
    BiquadFilter highPassHigh;
    BiquadFilter lowPassHigh;
    BiquadFilter gustFilter;
    
    LFO lfoSlow;
    LFO lfoMedium;
    LFO lfoFast;
    LFO lfoGust;
    
    GustGenerator gustGen;
    
    // Time tracking
    float sampleTime = 0.0f;
    float gustTimer = 0.0f;
    
    // Internal methods
    void generateSamples(float* output, int frameCount);
    float synthesizeSample();
    void updateFilters();
    void updateModulation();
    void checkForGust(float dt);
    
    // Audio callback (static for raylib)
    static void audioCallback(void* bufferData, unsigned int frames);
    static WindSoundSynthesizer* activeInstance;
};

} // namespace ethereal
