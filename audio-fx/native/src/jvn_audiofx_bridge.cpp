#include "beez_renderer.hpp"
#include "loom_ambience_renderer.hpp"

#include <jni.h>

#include <memory>
#include <new>
#include <string>

namespace {

using jvn::audiofx::BeezRenderer;
using jvn::audiofx::LoomAmbienceRenderer;

std::string toUtf8(JNIEnv* env, jstring value) {
  if (value == nullptr) return {};
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) return {};
  std::string out(chars);
  env->ReleaseStringUTFChars(value, chars);
  return out;
}

jstring makeString(JNIEnv* env, const char* value) {
  return env->NewStringUTF(value);
}

BeezRenderer* fromBeez(jlong handle) {
  return reinterpret_cast<BeezRenderer*>(handle);
}

LoomAmbienceRenderer* fromAmbience(jlong handle) {
  return reinterpret_cast<LoomAmbienceRenderer*>(handle);
}

template <typename RenderFn>
jint renderIntoByteArray(JNIEnv* env, jbyteArray pcm, int requestedFrames, RenderFn&& renderFn) {
  if (pcm == nullptr || requestedFrames <= 0) return 0;
  const jsize arrayLength = env->GetArrayLength(pcm);
  const int maxFrames = static_cast<int>(arrayLength / 4);
  const int frames = requestedFrames < maxFrames ? requestedFrames : maxFrames;
  if (frames <= 0) return 0;

  jboolean isCopy = JNI_FALSE;
  jbyte* bytes = env->GetByteArrayElements(pcm, &isCopy);
  if (bytes == nullptr) return 0;
  const int written = renderFn(reinterpret_cast<uint8_t*>(bytes), frames);
  env->ReleaseByteArrayElements(pcm, bytes, 0);
  return written;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nBridgeInfo(
    JNIEnv* env, jclass) {
  return makeString(env, "jvn-audiofx-native:beez+loom");
}

JNIEXPORT jlong JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nCreateBeezRenderer(
    JNIEnv*, jclass, jint sampleRate) {
  try {
    return reinterpret_cast<jlong>(new BeezRenderer(sampleRate));
  } catch (const std::bad_alloc&) {
    return 0;
  }
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nConfigureBeez(
    JNIEnv* env, jclass, jlong handle, jstring cueId, jfloat intensity, jfloat volume, jboolean loop) {
  if (BeezRenderer* renderer = fromBeez(handle)) {
    renderer->configure(toUtf8(env, cueId), intensity, volume, loop == JNI_TRUE);
  }
}

JNIEXPORT jint JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nRenderBeez(
    JNIEnv* env, jclass, jlong handle, jbyteArray pcm, jint frames) {
  if (BeezRenderer* renderer = fromBeez(handle)) {
    return renderIntoByteArray(env, pcm, frames, [renderer](uint8_t* dst, int frameCount) {
      return renderer->render(dst, frameCount);
    });
  }
  return 0;
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nSetBeezVolume(
    JNIEnv*, jclass, jlong handle, jfloat volume) {
  if (BeezRenderer* renderer = fromBeez(handle)) {
    renderer->setVolume(volume);
  }
}

JNIEXPORT jboolean JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nIsBeezFinished(
    JNIEnv*, jclass, jlong handle) {
  if (BeezRenderer* renderer = fromBeez(handle)) {
    return renderer->finished() ? JNI_TRUE : JNI_FALSE;
  }
  return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nStopBeez(
    JNIEnv*, jclass, jlong handle) {
  if (BeezRenderer* renderer = fromBeez(handle)) {
    renderer->stop();
  }
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nDestroyBeezRenderer(
    JNIEnv*, jclass, jlong handle) {
  delete fromBeez(handle);
}

JNIEXPORT jlong JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nCreateAmbienceRenderer(
    JNIEnv*, jclass, jint sampleRate) {
  try {
    return reinterpret_cast<jlong>(new LoomAmbienceRenderer(sampleRate));
  } catch (const std::bad_alloc&) {
    return 0;
  }
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nConfigureAmbience(
    JNIEnv* env, jclass, jlong handle, jstring preset, jfloat intensity, jfloat volume, jboolean loop) {
  if (LoomAmbienceRenderer* renderer = fromAmbience(handle)) {
    renderer->configure(toUtf8(env, preset), intensity, volume, loop == JNI_TRUE);
  }
}

JNIEXPORT jint JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nRenderAmbience(
    JNIEnv* env, jclass, jlong handle, jbyteArray pcm, jint frames) {
  if (LoomAmbienceRenderer* renderer = fromAmbience(handle)) {
    return renderIntoByteArray(env, pcm, frames, [renderer](uint8_t* dst, int frameCount) {
      return renderer->render(dst, frameCount);
    });
  }
  return 0;
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nSetAmbienceVolume(
    JNIEnv*, jclass, jlong handle, jfloat volume) {
  if (LoomAmbienceRenderer* renderer = fromAmbience(handle)) {
    renderer->setVolume(volume);
  }
}

JNIEXPORT jboolean JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nIsAmbienceFinished(
    JNIEnv*, jclass, jlong handle) {
  if (LoomAmbienceRenderer* renderer = fromAmbience(handle)) {
    return renderer->finished() ? JNI_TRUE : JNI_FALSE;
  }
  return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nStopAmbience(
    JNIEnv*, jclass, jlong handle) {
  if (LoomAmbienceRenderer* renderer = fromAmbience(handle)) {
    renderer->stop();
  }
}

JNIEXPORT void JNICALL Java_com_jvn_audiofx_AudioFxNativeBridge_nDestroyAmbienceRenderer(
    JNIEnv*, jclass, jlong handle) {
  delete fromAmbience(handle);
}

}  // extern "C"
