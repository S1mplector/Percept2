#pragma once

#include <cstdint>
#include <memory>
#include <string>

namespace jvn::audiofx {

class LoomAmbienceRenderer {
public:
  explicit LoomAmbienceRenderer(int sampleRate);
  ~LoomAmbienceRenderer();

  LoomAmbienceRenderer(LoomAmbienceRenderer&&) noexcept;
  LoomAmbienceRenderer& operator=(LoomAmbienceRenderer&&) noexcept;

  LoomAmbienceRenderer(const LoomAmbienceRenderer&) = delete;
  LoomAmbienceRenderer& operator=(const LoomAmbienceRenderer&) = delete;

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
  class Impl;
  std::unique_ptr<Impl> impl_;
};

}  // namespace jvn::audiofx
