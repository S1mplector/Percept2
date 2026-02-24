#include <jni.h>
#include <cstdint>
#include <limits>

#include "simjot_native.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jvn_core_nativebridge_NativeIoBridge_atomicWriteNative(
    JNIEnv* env,
    jclass,
    jstring targetPath,
    jbyteArray data,
    jboolean fsyncFile,
    jboolean fsyncDir
) {
    if (!env || !targetPath || !data) return JNI_FALSE;

    const char* pathChars = env->GetStringUTFChars(targetPath, nullptr);
    if (!pathChars) return JNI_FALSE;

    jsize len = env->GetArrayLength(data);
    if (len < 0 || len > static_cast<jsize>(std::numeric_limits<int32_t>::max())) {
        env->ReleaseStringUTFChars(targetPath, pathChars);
        return JNI_FALSE;
    }

    jbyte* raw = env->GetByteArrayElements(data, nullptr);
    if (!raw) {
        env->ReleaseStringUTFChars(targetPath, pathChars);
        return JNI_FALSE;
    }

    const int32_t ok = simjot_atomic_write(
        pathChars,
        reinterpret_cast<const uint8_t*>(raw),
        static_cast<int32_t>(len),
        fsyncFile ? 1 : 0,
        fsyncDir ? 1 : 0
    );

    env->ReleaseByteArrayElements(data, raw, JNI_ABORT);
    env->ReleaseStringUTFChars(targetPath, pathChars);
    return ok ? JNI_TRUE : JNI_FALSE;
}
