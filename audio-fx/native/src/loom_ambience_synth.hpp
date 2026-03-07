#pragma once

#include <cstdint>
#include <memory>
#include <string>

#include "ambience/ambience_dsp.hpp"
#include "ambience/ambience_mode.hpp"

namespace jvn::audiofx::detail {

enum class AmbiencePreset { Wind, Rain, Ocean, Thunder, Fireplace, NightInsects };

class LoomAmbienceSynthCore {
public:
  explicit LoomAmbienceSynthCore(int sampleRate);

  void configure(
      const std::string& preset,
      float intensity,
      float volume,
      float detail,
      float motion,
      float spread,
      float accent,
      bool loop);
  int render(uint8_t* pcm, int frames);
  void stop();
  void setVolume(float volume);
  bool finished() const noexcept;

private:
  static AmbiencePreset presetFromToken(const std::string& token);
  void selectMode(AmbiencePreset preset);
  float nextMonoSample();

  int sampleRate_;
  AmbiencePreset preset_ = AmbiencePreset::Wind;
  RenderControls controls_{};
  bool finished_ = false;
  float elapsedSeconds_ = 0.0f;
  Lfo panLfo_{0.11f, 0.1f, 0x76543210u};
  MasterState master_{};
  std::unique_ptr<AmbienceMode> mode_;
};

}  // namespace jvn::audiofx::detail
