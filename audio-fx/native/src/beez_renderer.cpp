#include "beez_renderer.hpp"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <cstring>
#include <sstream>
#include <string>

namespace jvn::audiofx {
namespace {

constexpr int kChannels = 2;
constexpr int kBytesPerSample = 2;
constexpr int kFrameBytes = kChannels * kBytesPerSample;

struct Preset {
  const char* name;
  std::vector<double> frequencies;
  std::vector<int> durationsMs;
};

std::string normalizeToken(const std::string& input) {
  std::string out;
  out.reserve(input.size());
  for (unsigned char c : input) {
    if (std::isalnum(c)) {
      out.push_back(static_cast<char>(std::tolower(c)));
    }
  }
  return out;
}

const Preset* resolveNamedPreset(const std::string& cueId) {
  static const std::array<Preset, 4> kPresets = {{
      {"blip", {880.0}, {120}},
      {"confirm", {660.0, 880.0, 1320.0}, {100, 100, 140}},
      {"error", {320.0, 240.0}, {180, 240}},
      {"pickup", {440.0, 660.0, 880.0, 1320.0}, {90, 90, 90, 140}},
  }};

  const std::string key = normalizeToken(cueId);
  if (key.empty() || key == "blip" || key == "beep") return &kPresets[0];
  if (key.find("confirm") != std::string::npos || key == "success") return &kPresets[1];
  if (key.find("error") != std::string::npos || key == "fail") return &kPresets[2];
  if (key.find("pickup") != std::string::npos || key == "powerup") return &kPresets[3];
  return nullptr;
}

short clampPcm16(float value) {
  const float clamped = std::max(-1.0f, std::min(1.0f, value));
  return static_cast<short>(std::lrintf(clamped * 32767.0f));
}

}  // namespace

BeezRenderer::BeezRenderer(int sampleRate) : sampleRate_(sampleRate > 1 ? sampleRate : 44100) {
  resetEngine();
}

void BeezRenderer::configure(const std::string& cueId, float intensity, float volume, bool loop) {
  intensity_ = clamp01(intensity);
  volume_ = clamp01(volume);
  loop_ = loop;
  rebuildSequence(cueId);
  resetEngine();
  eventIndex_ = 0;
  framesRemaining_ = 0;
  eventStarted_ = false;
  finished_ = events_.empty();
  configured_ = true;
}

int BeezRenderer::render(uint8_t* pcm, int frames) {
  if (!pcm || frames <= 0) return 0;
  const int totalBytes = frames * kFrameBytes;
  std::memset(pcm, 0, totalBytes);
  if (!configured_ || finished_) return totalBytes;

  int renderedFrames = 0;
  while (renderedFrames < frames) {
    if (finished_) break;
    if (!eventStarted_) beginCurrentEvent();
    if (finished_) break;
    if (framesRemaining_ <= 0) {
      advanceEvent();
      continue;
    }

    const int chunk = std::min(frames - renderedFrames, framesRemaining_);
    writeChunk(pcm + renderedFrames * kFrameBytes, chunk);
    renderedFrames += chunk;
    framesRemaining_ -= chunk;

    if (framesRemaining_ <= 0) {
      advanceEvent();
    }
  }

  return totalBytes;
}

void BeezRenderer::stop() {
  finished_ = true;
  configured_ = false;
  framesRemaining_ = 0;
  eventStarted_ = false;
  synth_engine_reset(&engine_);
}

void BeezRenderer::setVolume(float volume) {
  volume_ = clamp01(volume);
  synth_engine_set_master_volume(&engine_, volume_ * (0.30f + 0.70f * intensity_));
}

bool BeezRenderer::finished() const noexcept {
  return finished_;
}

float BeezRenderer::clamp01(float value) {
  return std::max(0.0f, std::min(1.0f, value));
}

void BeezRenderer::resetEngine() {
  synth_engine_init(&engine_, static_cast<float>(sampleRate_));
  synth_engine_set_master_volume(&engine_, volume_ * (0.30f + 0.70f * intensity_));

  const OscillatorWaveform leadWaveform = intensity_ >= 0.82f
      ? OSC_PULSE_25
      : (intensity_ >= 0.56f ? OSC_SQUARE : OSC_TRIANGLE);

  synth_engine_set_channel_waveform(&engine_, 0, leadWaveform);
  synth_engine_set_channel_adsr(&engine_, 0, 0.002f, 0.045f, 0.18f, 0.045f);
  synth_engine_set_channel_volume(&engine_, 0, 0.90f);
  synth_engine_set_channel_pan(&engine_, 0, -0.14f);
  synth_engine_set_channel_enabled(&engine_, 0, true);

  synth_engine_set_channel_waveform(&engine_, 1, OSC_TRIANGLE);
  synth_engine_set_channel_adsr(&engine_, 1, 0.001f, 0.06f, 0.10f, 0.05f);
  synth_engine_set_channel_volume(&engine_, 1, 0.42f + 0.18f * intensity_);
  synth_engine_set_channel_pan(&engine_, 1, 0.14f);
  synth_engine_set_channel_enabled(&engine_, 1, true);

  for (int channel = 2; channel < BEEZ_MAX_CHANNELS; ++channel) {
    synth_engine_set_channel_enabled(&engine_, channel, false);
  }
}

void BeezRenderer::rebuildSequence(const std::string& cueId) {
  events_.clear();
  const Preset* preset = resolveNamedPreset(cueId);
  if (preset != nullptr) {
    for (std::size_t i = 0; i < preset->frequencies.size(); ++i) {
      const int note = frequency_to_note(static_cast<float>(preset->frequencies[i]));
      const int noteFrames = std::max(1, static_cast<int>(sampleRate_ * (preset->durationsMs[i] / 1000.0)));
      events_.push_back(Event{note, noteFrames, false});
      events_.push_back(Event{note, std::max(1, sampleRate_ * 18 / 1000), true});
    }
    return;
  }

  std::stringstream ss(cueId);
  std::string part;
  while (std::getline(ss, part, ',')) {
    try {
      const double frequency = std::stod(part);
      const int note = frequency_to_note(static_cast<float>(frequency));
      events_.push_back(Event{note, std::max(1, sampleRate_ * 110 / 1000), false});
      events_.push_back(Event{note, std::max(1, sampleRate_ * 18 / 1000), true});
    } catch (...) {
      // Ignore malformed fragments and fall back below when nothing valid was parsed.
    }
  }

  if (events_.empty()) {
    const Preset* fallback = resolveNamedPreset("blip");
    for (std::size_t i = 0; i < fallback->frequencies.size(); ++i) {
      const int note = frequency_to_note(static_cast<float>(fallback->frequencies[i]));
      const int noteFrames = std::max(1, static_cast<int>(sampleRate_ * (fallback->durationsMs[i] / 1000.0)));
      events_.push_back(Event{note, noteFrames, false});
      events_.push_back(Event{note, std::max(1, sampleRate_ * 18 / 1000), true});
    }
  }
}

void BeezRenderer::beginCurrentEvent() {
  if (events_.empty()) {
    finished_ = true;
    return;
  }
  if (eventIndex_ >= events_.size()) {
    if (!loop_) {
      finished_ = true;
      synth_engine_note_off(&engine_, 0);
      synth_engine_note_off(&engine_, 1);
      return;
    }
    eventIndex_ = 0;
  }

  const Event& event = events_[eventIndex_];
  framesRemaining_ = std::max(1, event.durationFrames);
  eventStarted_ = true;

  if (event.rest) {
    synth_engine_note_off(&engine_, 0);
    synth_engine_note_off(&engine_, 1);
    return;
  }

  synth_engine_note_on(&engine_, 0, event.note, 0.92f);
  const int subNote = std::max(24, event.note - 12);
  synth_engine_note_on(&engine_, 1, subNote, 0.50f + 0.18f * intensity_);
}

void BeezRenderer::advanceEvent() {
  if (events_.empty()) {
    finished_ = true;
    return;
  }
  ++eventIndex_;
  eventStarted_ = false;
  if (!loop_ && eventIndex_ >= events_.size()) {
    finished_ = true;
  }
}

void BeezRenderer::writeChunk(uint8_t* dst, int frames) {
  left_.assign(frames, 0.0f);
  right_.assign(frames, 0.0f);
  synth_engine_generate_stereo(&engine_, left_.data(), right_.data(), frames);

  for (int i = 0; i < frames; ++i) {
    const short left = clampPcm16(left_[i]);
    const short right = clampPcm16(right_[i]);
    const int offset = i * kFrameBytes;
    dst[offset] = static_cast<uint8_t>(left & 0xFF);
    dst[offset + 1] = static_cast<uint8_t>((left >> 8) & 0xFF);
    dst[offset + 2] = static_cast<uint8_t>(right & 0xFF);
    dst[offset + 3] = static_cast<uint8_t>((right >> 8) & 0xFF);
  }
}

}  // namespace jvn::audiofx
