#include "loom_ambience_synth.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstring>

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

short clampPcm16(float value) {
  const float clamped = std::max(-1.0f, std::min(1.0f, value));
  return static_cast<short>(std::lrintf(clamped * 32767.0f));
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
  controls_.intensity = clamp01(intensity);
  controls_.volume = clamp01(volume);
  controls_.detail = clamp01(detail);
  controls_.motion = clamp01(motion);
  controls_.spread = clamp01(spread);
  controls_.accent = clamp01(accent);
  controls_.loop = loop;

  if (!mode_ || nextPreset != preset_) {
    selectMode(nextPreset);
  }
  preset_ = nextPreset;
  finished_ = false;
  elapsedSeconds_ = 0.0f;
  panLfo_.reset();
  master_.reset();
  mode_->configure(controls_);
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
  const float sample = mode_->sample(elapsedSeconds_);
  elapsedSeconds_ += 1.0f / sampleRate_;
  if (!controls_.loop && elapsedSeconds_ >= kAutoStopSeconds) {
    finished_ = true;
  }
  return master_.process(sample * controls_.volume * (0.18f + 0.82f * controls_.intensity));
}

}  // namespace jvn::audiofx::detail
