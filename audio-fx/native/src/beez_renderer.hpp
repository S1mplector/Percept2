#pragma once

#include <cstdint>
#include <string>
#include <vector>

extern "C" {
#include "core/engine/synth_engine.h"
}

namespace jvn::audiofx {

class BeezRenderer {
public:
  explicit BeezRenderer(int sampleRate);

  void configure(const std::string& cueId, float intensity, float volume, bool loop);
  int render(uint8_t* pcm, int frames);
  void stop();
  void setVolume(float volume);
  bool finished() const noexcept;

private:
  struct Event {
    int note = 69;
    int durationFrames = 0;
    bool rest = false;
  };

  static float clamp01(float value);
  void resetEngine();
  void rebuildSequence(const std::string& cueId);
  void beginCurrentEvent();
  void advanceEvent();
  void writeChunk(uint8_t* dst, int frames);

  int sampleRate_;
  SynthEngine engine_{};
  bool configured_ = false;
  bool loop_ = false;
  bool finished_ = true;
  float intensity_ = 0.85f;
  float volume_ = 0.7f;
  std::vector<Event> events_;
  std::size_t eventIndex_ = 0;
  int framesRemaining_ = 0;
  bool eventStarted_ = false;
  std::vector<float> left_;
  std::vector<float> right_;
};

}  // namespace jvn::audiofx
