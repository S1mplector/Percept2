#include "loom_ambience_renderer.hpp"

#include <memory>
#include <utility>

#include "loom_ambience_synth.hpp"

namespace jvn::audiofx {

class LoomAmbienceRenderer::Impl {
public:
  explicit Impl(int sampleRate) : core(sampleRate) {}

  detail::LoomAmbienceSynthCore core;
};

LoomAmbienceRenderer::LoomAmbienceRenderer(int sampleRate) : impl_(std::make_unique<Impl>(sampleRate)) {}

LoomAmbienceRenderer::~LoomAmbienceRenderer() = default;
LoomAmbienceRenderer::LoomAmbienceRenderer(LoomAmbienceRenderer&&) noexcept = default;
LoomAmbienceRenderer& LoomAmbienceRenderer::operator=(LoomAmbienceRenderer&&) noexcept = default;

void LoomAmbienceRenderer::configure(
    const std::string& preset,
    float intensity,
    float volume,
    float detail,
    float motion,
    float spread,
    float accent,
    bool loop) {
  impl_->core.configure(preset, intensity, volume, detail, motion, spread, accent, loop);
}

int LoomAmbienceRenderer::render(uint8_t* pcm, int frames) {
  return impl_->core.render(pcm, frames);
}

void LoomAmbienceRenderer::stop() {
  impl_->core.stop();
}

void LoomAmbienceRenderer::setVolume(float volume) {
  impl_->core.setVolume(volume);
}

bool LoomAmbienceRenderer::finished() const noexcept {
  return impl_->core.finished();
}

}  // namespace jvn::audiofx
