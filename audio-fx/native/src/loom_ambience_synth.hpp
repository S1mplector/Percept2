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
  RenderControls previousControls_{};
  bool finished_ = false;
  bool configured_ = false;
  float elapsedSeconds_ = 0.0f;
  float previousElapsedSeconds_ = 0.0f;
  MasterState master_{};
  StereoFieldState stereoField_{};
  std::unique_ptr<AmbienceMode> mode_;
  std::unique_ptr<AmbienceMode> previousMode_;
  int crossfadeSamplesRemaining_ = 0;
  int crossfadeSamplesTotal_ = 0;
};

}  // namespace jvn::audiofx::detail
