#include "loom_ambience_synth.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>
#include <utility>

#include "ambience/ambience_mode.hpp"
#include "ambience/fireplace_ambience_mode.hpp"
#include "ambience/night_insects_ambience_mode.hpp"
#include "ambience/ocean_ambience_mode.hpp"
#include "ambience/rain_ambience_mode.hpp"
#include "ambience/thunder_ambience_mode.hpp"
#include "ambience/wind_ambience_mode.hpp"

namespace jvn::audiofx::detail {
namespace {

constexpr int kChannels = 2;
constexpr int kBytesPerSample = 2;
constexpr int kFrameBytes = kChannels * kBytesPerSample;
constexpr float kAutoStopSeconds = 15.0f;
constexpr float kModeCrossfadeSeconds = 0.65f;

short clampPcm16(float value) {
  const float clamped = std::max(-1.0f, std::min(1.0f, value));
  return static_cast<short>(std::lrintf(clamped * 32767.0f));
}

float renderGain(const RenderControls& controls) {
  return controls.volume * (0.18f + 0.82f * controls.intensity);
}

float smoothstep01(float value) {
  const float t = std::clamp(value, 0.0f, 1.0f);
  return t * t * (3.0f - 2.0f * t);
}

}  // namespace

LoomAmbienceSynthCore::LoomAmbienceSynthCore(int sampleRate) : sampleRate_(sampleRate > 1 ? sampleRate : 44100) {
  const float sampleRateF = static_cast<float>(sampleRate_);
  panLfo_.setSampleRate(sampleRateF);
  master_.setSampleRate(sampleRateF);
  selectMode(preset_);
}

void LoomAmbienceSynthCore::configure(
    const std::string& preset,
    float intensity,
    float volume,
    float detail,
    float motion,
    float spread,
    float accent,
    bool loop) {
  const AmbiencePreset nextPreset = presetFromToken(preset);
  RenderControls nextControls{};
  nextControls.intensity = clamp01(intensity);
  nextControls.volume = clamp01(volume);
  nextControls.detail = clamp01(detail);
  nextControls.motion = clamp01(motion);
  nextControls.spread = clamp01(spread);
  nextControls.accent = clamp01(accent);
  nextControls.loop = loop;

  const bool needsFullConfigure = !mode_ || !configured_;
  const bool presetChanged = !needsFullConfigure && nextPreset != preset_;

  if (needsFullConfigure) {
    if (!mode_ || nextPreset != preset_) {
      selectMode(nextPreset);
    }
    controls_ = nextControls;
    preset_ = nextPreset;
    finished_ = false;
    configured_ = true;
    elapsedSeconds_ = 0.0f;
    previousElapsedSeconds_ = 0.0f;
    crossfadeSamplesRemaining_ = 0;
    crossfadeSamplesTotal_ = 0;
    previousMode_.reset();
    panLfo_.reset();
    master_.reset();
    mode_->configure(controls_);
    return;
  }

  if (presetChanged) {
    previousMode_ = std::move(mode_);
    previousControls_ = controls_;
    previousElapsedSeconds_ = elapsedSeconds_;
    selectMode(nextPreset);
    controls_ = nextControls;
    preset_ = nextPreset;
    finished_ = false;
    elapsedSeconds_ = 0.0f;
    crossfadeSamplesTotal_ = std::max(1, static_cast<int>(std::lround(sampleRate_ * kModeCrossfadeSeconds)));
    crossfadeSamplesRemaining_ = crossfadeSamplesTotal_;
    mode_->configure(controls_);
    return;
  }

  controls_ = nextControls;
  finished_ = false;
  mode_->retune(controls_);
}

int LoomAmbienceSynthCore::render(uint8_t* pcm, int frames) {
  if (!pcm || frames <= 0) return 0;
  const int totalBytes = frames * kFrameBytes;
  std::memset(pcm, 0, totalBytes);
  if (finished_) return totalBytes;

  for (int i = 0; i < frames; ++i) {
    const float mono = nextMonoSample();
    const float pan = (0.06f + controls_.spread * 0.24f) * panLfo_.sine();
    panLfo_.advance();
    const short left = clampPcm16(mono * (1.0f - pan));
    const short right = clampPcm16(mono * (1.0f + pan));
    const int offset = i * kFrameBytes;
    pcm[offset] = static_cast<uint8_t>(left & 0xFF);
    pcm[offset + 1] = static_cast<uint8_t>((left >> 8) & 0xFF);
    pcm[offset + 2] = static_cast<uint8_t>(right & 0xFF);
    pcm[offset + 3] = static_cast<uint8_t>((right >> 8) & 0xFF);
  }

  return totalBytes;
}

void LoomAmbienceSynthCore::stop() {
  finished_ = true;
}

void LoomAmbienceSynthCore::setVolume(float volume) {
  controls_.volume = clamp01(volume);
}

bool LoomAmbienceSynthCore::finished() const noexcept {
  return finished_;
}

AmbiencePreset LoomAmbienceSynthCore::presetFromToken(const std::string& token) {
  std::string normalized;
  normalized.reserve(token.size());
  for (unsigned char c : token) {
    if (std::isalnum(c)) normalized.push_back(static_cast<char>(std::tolower(c)));
  }
  if (normalized.find("rain") != std::string::npos || normalized.find("drizzle") != std::string::npos ||
      normalized.find("storm") != std::string::npos) {
    return AmbiencePreset::Rain;
  }
  if (normalized.find("ocean") != std::string::npos || normalized.find("wave") != std::string::npos ||
      normalized.find("sea") != std::string::npos || normalized.find("surf") != std::string::npos) {
    return AmbiencePreset::Ocean;
  }
  if (normalized.find("thunder") != std::string::npos || normalized.find("lightning") != std::string::npos) {
    return AmbiencePreset::Thunder;
  }
  if (normalized.find("fire") != std::string::npos || normalized.find("hearth") != std::string::npos ||
      normalized.find("campfire") != std::string::npos) {
    return AmbiencePreset::Fireplace;
  }
  if (normalized.find("insect") != std::string::npos || normalized.find("cricket") != std::string::npos ||
      normalized.find("cicada") != std::string::npos || normalized.find("night") != std::string::npos) {
    return AmbiencePreset::NightInsects;
  }
  return AmbiencePreset::Wind;
}

void LoomAmbienceSynthCore::selectMode(AmbiencePreset preset) {
  switch (preset) {
    case AmbiencePreset::Wind:
      mode_ = std::make_unique<WindAmbienceMode>(sampleRate_);
      break;
    case AmbiencePreset::Rain:
      mode_ = std::make_unique<RainAmbienceMode>(sampleRate_);
      break;
    case AmbiencePreset::Ocean:
      mode_ = std::make_unique<OceanAmbienceMode>(sampleRate_);
      break;
    case AmbiencePreset::Thunder:
      mode_ = std::make_unique<ThunderAmbienceMode>(sampleRate_);
      break;
    case AmbiencePreset::Fireplace:
      mode_ = std::make_unique<FireplaceAmbienceMode>(sampleRate_);
      break;
    case AmbiencePreset::NightInsects:
      mode_ = std::make_unique<NightInsectsAmbienceMode>(sampleRate_);
      break;
  }
}

float LoomAmbienceSynthCore::nextMonoSample() {
  if (finished_ || !mode_) return 0.0f;
  float sample = mode_->sample(elapsedSeconds_) * renderGain(controls_);
  elapsedSeconds_ += 1.0f / sampleRate_;
  if (previousMode_) {
    float previousSample = previousMode_->sample(previousElapsedSeconds_) * renderGain(previousControls_);
    previousElapsedSeconds_ += 1.0f / sampleRate_;
    float fadeIn = 1.0f;
    if (crossfadeSamplesTotal_ > 0 && crossfadeSamplesRemaining_ > 0) {
      const float progress =
          1.0f - (static_cast<float>(crossfadeSamplesRemaining_) / static_cast<float>(crossfadeSamplesTotal_));
      fadeIn = smoothstep01(progress);
      crossfadeSamplesRemaining_--;
    }
    sample = previousSample * (1.0f - fadeIn) + sample * fadeIn;
    if (crossfadeSamplesRemaining_ <= 0) {
      previousMode_.reset();
      crossfadeSamplesTotal_ = 0;
      crossfadeSamplesRemaining_ = 0;
    }
  }
  if (!controls_.loop && elapsedSeconds_ >= kAutoStopSeconds) {
    finished_ = true;
  }
  return master_.process(sample);
}

}  // namespace jvn::audiofx::detail
