#include "WindSoundSynthesizer.hpp"
#include <algorithm>
#include <cstring>

namespace ethereal {

// Static instance pointer for audio callback
WindSoundSynthesizer* WindSoundSynthesizer::activeInstance = nullptr;

// ============================================================================
// BiquadFilter Implementation
// ============================================================================

void BiquadFilter::setCoefficients(Type type, float frequency, float q, float sampleRate) {
    float omega = 2.0f * 3.14159265f * frequency / sampleRate;
    float sinOmega = std::sin(omega);
    float cosOmega = std::cos(omega);
    float alpha = sinOmega / (2.0f * q);
    
    float a0;
    
    switch (type) {
        case Type::LowPass:
            b0 = (1.0f - cosOmega) / 2.0f;
            b1 = 1.0f - cosOmega;
            b2 = (1.0f - cosOmega) / 2.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosOmega;
            a2 = 1.0f - alpha;
            break;
            
        case Type::HighPass:
            b0 = (1.0f + cosOmega) / 2.0f;
            b1 = -(1.0f + cosOmega);
            b2 = (1.0f + cosOmega) / 2.0f;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosOmega;
            a2 = 1.0f - alpha;
            break;
            
        case Type::BandPass:
            b0 = alpha;
            b1 = 0.0f;
            b2 = -alpha;
            a0 = 1.0f + alpha;
            a1 = -2.0f * cosOmega;
            a2 = 1.0f - alpha;
            break;
    }
    
    // Normalize coefficients
    b0 /= a0;
    b1 /= a0;
    b2 /= a0;
    a1 /= a0;
    a2 /= a0;
}

float BiquadFilter::process(float input) {
    float output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    
    x2 = x1;
    x1 = input;
    y2 = y1;
    y1 = output;
    
    // Prevent denormals
    if (std::abs(y1) < 1e-15f) y1 = 0.0f;
    if (std::abs(y2) < 1e-15f) y2 = 0.0f;
    
    return output;
}

void BiquadFilter::reset() {
    x1 = x2 = y1 = y2 = 0.0f;
}

// ============================================================================
// LFO Implementation
// ============================================================================

float LFO::sine() {
    return std::sin(phase * 2.0f * 3.14159265f);
}

float LFO::triangle() {
    float t = std::fmod(phase, 1.0f);
    return 4.0f * std::abs(t - 0.5f) - 1.0f;
}

float LFO::smoothRandom() {
    // Interpolated random values for organic movement
    static std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
    
    // Update target periodically
    float cyclePos = std::fmod(phase * 4.0f, 1.0f);
    if (cyclePos < 0.01f) {
        targetValue = dist(rng);
    }
    
    // Smooth interpolation toward target
    smoothValue += (targetValue - smoothValue) * 0.001f;
    return smoothValue;
}

void LFO::advance() {
    phase += rate / sampleRate;
    if (phase > 1000.0f) phase -= 1000.0f;  // Prevent overflow
}

// ============================================================================
// NoiseGenerator Implementation
// ============================================================================

NoiseGenerator::NoiseGenerator() : rng(std::random_device{}()) {
    pinkRows.fill(0.0f);
}

float NoiseGenerator::white() {
    return dist(rng);
}

float NoiseGenerator::pink() {
    // Voss-McCartney algorithm for pink noise
    float whiteNoise = dist(rng);
    
    pinkIndex = (pinkIndex + 1) & 15;
    
    if (pinkIndex != 0) {
        int numZeros = 0;
        int n = pinkIndex;
        while ((n & 1) == 0) {
            n >>= 1;
            numZeros++;
        }
        
        pinkRunningSum -= pinkRows[numZeros];
        float newRandom = dist(rng);
        pinkRunningSum += newRandom;
        pinkRows[numZeros] = newRandom;
    }
    
    return (pinkRunningSum + whiteNoise) / 5.0f;
}

float NoiseGenerator::brown() {
    // Brown noise: integrated white noise
    float whiteNoise = dist(rng);
    brownValue += whiteNoise * 0.02f;
    brownValue *= 0.998f;  // Leak to prevent drift
    return std::clamp(brownValue, -1.0f, 1.0f);
}

float NoiseGenerator::filtered(float cutoff, float resonance) {
    float whiteNoise = dist(rng);
    float alpha = cutoff;  // Simplified filter coefficient
    filterState = filterState + alpha * (whiteNoise - filterState);
    return filterState * (1.0f + resonance);
}

void NoiseGenerator::reset() {
    pinkRows.fill(0.0f);
    pinkIndex = 0;
    pinkRunningSum = 0.0f;
    brownValue = 0.0f;
    filterState = 0.0f;
}

// ============================================================================
// GustGenerator Implementation
// ============================================================================

GustGenerator::GustGenerator(float sampleRate) : sampleRate(sampleRate) {}

void GustGenerator::trigger(float gustIntensity) {
    if (active) return;  // Don't interrupt existing gust
    
    std::uniform_real_distribution<float> durDist(0.8f, 2.5f);
    std::uniform_real_distribution<float> attackDist(0.15f, 0.4f);
    
    active = true;
    phase = 0.0f;
    intensity = gustIntensity;
    duration = durDist(rng);
    attackTime = attackDist(rng) * duration;
    decayTime = duration - attackTime;
}

float GustGenerator::generate() {
    if (!active) return 0.0f;
    
    float time = phase / sampleRate;
    float envelope = 0.0f;
    
    if (time < attackTime) {
        // Attack phase - quick rise with slight overshoot
        float t = time / attackTime;
        envelope = t * t * (3.0f - 2.0f * t);  // Smoothstep
        envelope *= 1.0f + 0.2f * std::sin(t * 3.14159f);  // Slight overshoot
    } else if (time < duration) {
        // Decay phase - slow fade with ripples
        float t = (time - attackTime) / decayTime;
        envelope = 1.0f - t;
        envelope *= envelope;  // Quadratic decay
        // Add subtle ripples during decay
        envelope *= 1.0f + 0.1f * std::sin(t * 12.0f) * (1.0f - t);
    } else {
        active = false;
        return 0.0f;
    }
    
    return envelope * intensity;
}

void GustGenerator::update() {
    if (active) {
        phase += 1.0f;
    }
}

// ============================================================================
// WindSoundSynthesizer Implementation
// ============================================================================

WindSoundSynthesizer::WindSoundSynthesizer() : WindSoundSynthesizer(WindSoundConfig{}) {}

WindSoundSynthesizer::WindSoundSynthesizer(const WindSoundConfig& cfg) 
    : config(cfg)
    , buffer(BUFFER_SIZE * 2)
    , lfoSlow(cfg.slowLfoRate)
    , lfoMedium(cfg.mediumLfoRate)
    , lfoFast(cfg.fastLfoRate)
    , lfoGust(cfg.gustRate)
    , gustGen(cfg.sampleRate) {
    
    lfoSlow.setSampleRate(cfg.sampleRate);
    lfoMedium.setSampleRate(cfg.sampleRate);
    lfoFast.setSampleRate(cfg.sampleRate);
    lfoGust.setSampleRate(cfg.sampleRate);
}

WindSoundSynthesizer::~WindSoundSynthesizer() {
    if (initialized) {
        shutdown();
    }
}

void WindSoundSynthesizer::initialize() {
    if (initialized) return;
    
    InitAudioDevice();
    
    // Create audio stream: mono, 16-bit, at configured sample rate
    stream = LoadAudioStream((unsigned int)config.sampleRate, 16, 1);
    
    // Set up filters with initial values
    updateFilters();
    
    // Start playback
    activeInstance = this;
    SetAudioStreamCallback(stream, audioCallback);
    PlayAudioStream(stream);
    
    initialized = true;
}

void WindSoundSynthesizer::shutdown() {
    if (!initialized) return;
    
    StopAudioStream(stream);
    UnloadAudioStream(stream);
    CloseAudioDevice();
    
    activeInstance = nullptr;
    initialized = false;
}

void WindSoundSynthesizer::update(float dt, float playerSpeed, float windIntensity, float altitude) {
    if (!initialized || !enabled) return;
    
    // Normalize inputs
    playerSpeedNorm = std::clamp(playerSpeed / 200.0f, 0.0f, 1.0f);  // 200 = max expected speed
    windIntensityNorm = std::clamp(windIntensity / 100.0f, 0.0f, 1.0f);
    altitudeNorm = std::clamp(altitude / 500.0f, 0.0f, 1.0f);  // 500 = high altitude
    
    // Calculate target intensity from all factors
    float speedContrib = playerSpeedNorm * config.speedInfluence;
    float windContrib = windIntensityNorm * config.windInfluence;
    float altContrib = altitudeNorm * config.altitudeInfluence;
    
    // Combine with slight minimum so there's always some ambient wind
    targetIntensity = 0.08f + speedContrib * 0.5f + windContrib * 0.3f + altContrib * 0.2f;
    targetIntensity = std::clamp(targetIntensity, 0.0f, 1.0f);
    
    // Smooth intensity changes
    float smoothing = dt * 3.0f;
    currentIntensity += (targetIntensity - currentIntensity) * smoothing;
    
    // Check for random gusts
    checkForGust(dt);
    
    // Update filter parameters based on intensity
    updateFilters();
}

void WindSoundSynthesizer::setIntensity(float intensity) {
    targetIntensity = std::clamp(intensity, 0.0f, 1.0f);
}

void WindSoundSynthesizer::triggerGust(float intensity) {
    gustGen.trigger(intensity);
}

void WindSoundSynthesizer::setMasterVolume(float volume) {
    config.masterVolume = std::clamp(volume, 0.0f, 1.0f);
}

void WindSoundSynthesizer::setEnabled(bool isEnabled) {
    enabled = isEnabled;
    if (initialized) {
        if (enabled) {
            PlayAudioStream(stream);
        } else {
            PauseAudioStream(stream);
        }
    }
}

void WindSoundSynthesizer::setConfig(const WindSoundConfig& cfg) {
    config = cfg;
    
    lfoSlow.setRate(cfg.slowLfoRate);
    lfoSlow.setSampleRate(cfg.sampleRate);
    lfoMedium.setRate(cfg.mediumLfoRate);
    lfoMedium.setSampleRate(cfg.sampleRate);
    lfoFast.setRate(cfg.fastLfoRate);
    lfoFast.setSampleRate(cfg.sampleRate);
    lfoGust.setRate(cfg.gustRate);
    lfoGust.setSampleRate(cfg.sampleRate);
    
    gustGen.setSampleRate(cfg.sampleRate);
    
    updateFilters();
}

void WindSoundSynthesizer::audioCallback(void* bufferData, unsigned int frames) {
    if (!activeInstance || !activeInstance->enabled) {
        std::memset(bufferData, 0, frames * sizeof(short));
        return;
    }
    
    short* output = static_cast<short*>(bufferData);
    
    for (unsigned int i = 0; i < frames; ++i) {
        float sample = activeInstance->synthesizeSample();
        
        // Apply master volume and convert to 16-bit
        sample *= activeInstance->config.masterVolume;
        sample = std::clamp(sample, -1.0f, 1.0f);
        output[i] = static_cast<short>(sample * 32767.0f);
    }
}

float WindSoundSynthesizer::synthesizeSample() {
    // Get modulation values
    float slowMod = lfoSlow.sine() * 0.5f + 0.5f;      // 0 to 1
    float medMod = lfoMedium.triangle() * 0.5f + 0.5f; // 0 to 1
    float fastMod = lfoFast.sine() * 0.2f + 0.8f;      // 0.6 to 1 (less aggressive)
    float randomMod = lfoSlow.smoothRandom() * 0.15f + 0.85f;  // 0.7 to 1
    
    // Advance LFOs
    lfoSlow.advance();
    lfoMedium.advance();
    lfoFast.advance();
    
    // Base intensity with modulation - smoother overall
    float intensity = currentIntensity * slowMod * randomMod;
    intensity = std::sqrt(intensity);  // Softer response curve
    
    // ========================================
    // LAYER 1: Deep rumble (low frequencies)
    // ========================================
    float lowNoise = noiseGenLow.brown();
    lowNoise = lowPassLow.process(lowNoise);
    float lowLayer = lowNoise * config.lowWindVolume * intensity * 0.6f;
    
    // ========================================
    // LAYER 2: Main woosh (mid frequencies) - smoother
    // ========================================
    float midNoise = noiseGenMid.pink();
    midNoise = lowPassMid.process(midNoise);
    midNoise = highPassMid.process(midNoise);
    // Gentler brightness scaling
    float midBrightness = 1.0f + intensity * 0.3f;
    float midLayer = midNoise * config.midWindVolume * intensity * medMod * midBrightness * 0.85f;
    
    // ========================================
    // LAYER 3: High whistle (MUCH softer)
    // ========================================
    float highNoise = noiseGenHigh.pink();  // Pink instead of white for softer highs
    highNoise = highPassHigh.process(highNoise);
    highNoise = lowPassHigh.process(highNoise);
    // Very gentle high frequency response
    float highIntensity = std::pow(intensity, 3.0f);  // Cubic - only present at high speeds
    float highLayer = highNoise * config.highWindVolume * highIntensity * fastMod * 0.25f;  // Much quieter
    
    // ========================================
    // LAYER 4: Gusts (dynamic bursts) - gentler
    // ========================================
    float gustEnvelope = gustGen.generate();
    gustGen.update();
    
    float gustNoise = noiseGenGust.pink();
    gustNoise = gustFilter.process(gustNoise);
    float gustLayer = gustNoise * gustEnvelope * config.gustVolume * 0.7f;
    
    // ========================================
    // LAYER 5: Speed-dependent whoosh - smoother
    // ========================================
    float speedWhoosh = 0.0f;
    if (playerSpeedNorm > 0.35f) {
        float whooshNoise = noiseGenMid.filtered(0.08f + playerSpeedNorm * 0.2f, 0.3f);  // Lower resonance
        float whooshIntensity = (playerSpeedNorm - 0.35f) / 0.65f;
        whooshIntensity = std::sqrt(whooshIntensity);  // Softer curve
        speedWhoosh = whooshNoise * whooshIntensity * 0.3f * medMod;  // Use medium mod, not fast
    }
    
    // ========================================
    // LAYER 6: Altitude-dependent thin air - very subtle
    // ========================================
    float altitudeLayer = 0.0f;
    if (altitudeNorm > 0.5f) {
        float thinAirNoise = noiseGenHigh.pink() * 0.2f;  // Pink, not white
        float altIntensity = (altitudeNorm - 0.5f) / 0.5f;
        altIntensity = std::sqrt(altIntensity);
        altitudeLayer = thinAirNoise * altIntensity * 0.15f * (1.0f + slowMod * 0.2f);
    }
    
    // ========================================
    // Mix all layers with gentler balance
    // ========================================
    float mix = lowLayer + midLayer + highLayer + gustLayer + speedWhoosh + altitudeLayer;
    
    // Softer saturation curve
    mix = std::tanh(mix * 0.6f);  // Less aggressive saturation
    
    // Update sample time
    sampleTime += 1.0f / config.sampleRate;
    
    return mix;
}

void WindSoundSynthesizer::updateFilters() {
    float sampleRate = config.sampleRate;
    
    // Intensity affects filter cutoffs for more dynamic sound
    float intensityMod = 0.8f + currentIntensity * 0.4f;
    
    // Low rumble filter
    float lowCutoff = config.lowPassBase * intensityMod;
    lowPassLow.setCoefficients(BiquadFilter::Type::LowPass, lowCutoff, 0.7f, sampleRate);
    
    // Mid woosh bandpass
    float midLow = config.midLowPass * intensityMod;
    float midHigh = config.midHighPass;
    lowPassMid.setCoefficients(BiquadFilter::Type::LowPass, midLow, 0.5f, sampleRate);
    highPassMid.setCoefficients(BiquadFilter::Type::HighPass, midHigh, 0.5f, sampleRate);
    
    // High whistle filter - opens up with intensity
    float highCutoff = config.highPassBase + currentIntensity * 1500.0f;
    highPassHigh.setCoefficients(BiquadFilter::Type::HighPass, highCutoff, 0.6f, sampleRate);
    lowPassHigh.setCoefficients(BiquadFilter::Type::LowPass, 8000.0f, 0.4f, sampleRate);
    
    // Gust filter - mid-focused
    gustFilter.setCoefficients(BiquadFilter::Type::BandPass, 400.0f, 1.5f, sampleRate);
}

void WindSoundSynthesizer::checkForGust(float dt) {
    if (gustGen.isActive()) return;
    
    gustTimer += dt;
    
    // Random gust chance based on wind intensity
    float gustChance = config.gustRate * (0.3f + windIntensityNorm * 0.7f + playerSpeedNorm * 0.5f);
    
    if (gustTimer > 1.0f / gustChance) {
        // Trigger gust with random intensity
        float gustIntensity = 0.4f + (float)(rand() % 60) / 100.0f;
        gustIntensity *= (0.5f + currentIntensity * 0.5f);
        gustGen.trigger(gustIntensity);
        gustTimer = 0.0f;
    }
}

} // namespace ethereal
