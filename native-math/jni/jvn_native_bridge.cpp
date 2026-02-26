#include <jni.h>
#include <cstdint>
#include <limits>
#include <algorithm>
#include <vector>

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

extern "C" JNIEXPORT jdouble JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_dotProductNative(
    JNIEnv* env,
    jclass,
    jdoubleArray a,
    jdoubleArray b
) {
    if (!env || !a || !b) return 0.0;
    const jsize lenA = env->GetArrayLength(a);
    const jsize lenB = env->GetArrayLength(b);
    if (lenA <= 0 || lenB <= 0) return 0.0;
    const jsize n = lenA < lenB ? lenA : lenB;

    jboolean copyA = JNI_FALSE;
    jboolean copyB = JNI_FALSE;
    jdouble* rawA = env->GetDoubleArrayElements(a, &copyA);
    jdouble* rawB = env->GetDoubleArrayElements(b, &copyB);
    if (!rawA || !rawB) {
        if (rawA) env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
        if (rawB) env->ReleaseDoubleArrayElements(b, rawB, JNI_ABORT);
        return 0.0;
    }

    double sum = 0.0;
    for (jsize i = 0; i < n; ++i) {
        sum += static_cast<double>(rawA[i]) * static_cast<double>(rawB[i]);
    }

    env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
    env->ReleaseDoubleArrayElements(b, rawB, JNI_ABORT);
    return static_cast<jdouble>(sum);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_matMulNative(
    JNIEnv* env,
    jclass,
    jdoubleArray a,
    jdoubleArray b,
    jint m,
    jint n,
    jint p
) {
    if (!env || !a || !b || m <= 0 || n <= 0 || p <= 0) {
        return env ? env->NewDoubleArray(0) : nullptr;
    }

    const jsize lenA = env->GetArrayLength(a);
    const jsize lenB = env->GetArrayLength(b);
    const jsize needA = static_cast<jsize>(m) * static_cast<jsize>(n);
    const jsize needB = static_cast<jsize>(n) * static_cast<jsize>(p);
    if (lenA < needA || lenB < needB) return env->NewDoubleArray(0);

    jdouble* rawA = env->GetDoubleArrayElements(a, nullptr);
    jdouble* rawB = env->GetDoubleArrayElements(b, nullptr);
    if (!rawA || !rawB) {
        if (rawA) env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
        if (rawB) env->ReleaseDoubleArrayElements(b, rawB, JNI_ABORT);
        return env->NewDoubleArray(0);
    }

    std::vector<jdouble> out(static_cast<size_t>(m) * static_cast<size_t>(p), 0.0);
    for (jint i = 0; i < m; ++i) {
        const jint aRow = i * n;
        const jint cRow = i * p;
        for (jint k = 0; k < n; ++k) {
            const double aval = rawA[aRow + k];
            const jint bRow = k * p;
            for (jint j = 0; j < p; ++j) {
                out[static_cast<size_t>(cRow + j)] += aval * static_cast<double>(rawB[bRow + j]);
            }
        }
    }

    env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
    env->ReleaseDoubleArrayElements(b, rawB, JNI_ABORT);

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;
    env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_jvn_core_nativebridge_NativeSearchBridge_findCaseInsensitiveNative(
    JNIEnv* env,
    jclass,
    jstring haystack,
    jstring needle
) {
    if (!env || !haystack || !needle) return -1;

    const char* h = env->GetStringUTFChars(haystack, nullptr);
    const char* n = env->GetStringUTFChars(needle, nullptr);
    if (!h || !n) {
        if (h) env->ReleaseStringUTFChars(haystack, h);
        if (n) env->ReleaseStringUTFChars(needle, n);
        return -1;
    }

    const jsize hlen = env->GetStringUTFLength(haystack);
    const jsize nlen = env->GetStringUTFLength(needle);
    int64_t pos = -1;
    if (hlen > 0 && nlen > 0 && nlen <= hlen) {
        pos = simjot_search_find_ci(h, static_cast<int64_t>(hlen), n, static_cast<int64_t>(nlen));
    }

    env->ReleaseStringUTFChars(haystack, h);
    env->ReleaseStringUTFChars(needle, n);
    if (pos < 0 || pos > static_cast<int64_t>(std::numeric_limits<jint>::max())) return -1;
    return static_cast<jint>(pos);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_jvn_core_nativebridge_NativeSearchBridge_countCaseInsensitiveNative(
    JNIEnv* env,
    jclass,
    jstring haystack,
    jstring needle
) {
    if (!env || !haystack || !needle) return 0;

    const char* h = env->GetStringUTFChars(haystack, nullptr);
    const char* n = env->GetStringUTFChars(needle, nullptr);
    if (!h || !n) {
        if (h) env->ReleaseStringUTFChars(haystack, h);
        if (n) env->ReleaseStringUTFChars(needle, n);
        return 0;
    }

    const jsize hlen = env->GetStringUTFLength(haystack);
    const jsize nlen = env->GetStringUTFLength(needle);
    int32_t count = 0;
    if (hlen > 0 && nlen > 0 && nlen <= hlen) {
        count = simjot_search_count_ci(h, static_cast<int64_t>(hlen), n, static_cast<int64_t>(nlen));
    }

    env->ReleaseStringUTFChars(haystack, h);
    env->ReleaseStringUTFChars(needle, n);
    return static_cast<jint>(count);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_jvn_core_nativebridge_NativeSearchBridge_findAllCaseInsensitiveNative(
    JNIEnv* env,
    jclass,
    jstring haystack,
    jstring needle,
    jint maxResults
) {
    if (!env || !haystack || !needle || maxResults <= 0) {
        return env ? env->NewIntArray(0) : nullptr;
    }

    const char* h = env->GetStringUTFChars(haystack, nullptr);
    const char* n = env->GetStringUTFChars(needle, nullptr);
    if (!h || !n) {
        if (h) env->ReleaseStringUTFChars(haystack, h);
        if (n) env->ReleaseStringUTFChars(needle, n);
        return env->NewIntArray(0);
    }

    const jsize hlen = env->GetStringUTFLength(haystack);
    const jsize nlen = env->GetStringUTFLength(needle);
    if (hlen <= 0 || nlen <= 0 || nlen > hlen) {
        env->ReleaseStringUTFChars(haystack, h);
        env->ReleaseStringUTFChars(needle, n);
        return env->NewIntArray(0);
    }

    int32_t safeMax = std::min<int32_t>(maxResults, static_cast<int32_t>(hlen));
    safeMax = std::max<int32_t>(safeMax, 0);
    if (safeMax <= 0) {
        env->ReleaseStringUTFChars(haystack, h);
        env->ReleaseStringUTFChars(needle, n);
        return env->NewIntArray(0);
    }

    std::vector<int64_t> nativePositions(static_cast<size_t>(safeMax), -1);
    int32_t found = simjot_search_find_all_ci(
        h,
        static_cast<int64_t>(hlen),
        n,
        static_cast<int64_t>(nlen),
        nativePositions.data(),
        safeMax
    );

    env->ReleaseStringUTFChars(haystack, h);
    env->ReleaseStringUTFChars(needle, n);

    if (found <= 0) {
        return env->NewIntArray(0);
    }

    std::vector<jint> out(static_cast<size_t>(found), 0);
    for (int32_t i = 0; i < found; i++) {
        int64_t pos = nativePositions[static_cast<size_t>(i)];
        out[static_cast<size_t>(i)] = (pos >= 0 && pos <= std::numeric_limits<jint>::max())
            ? static_cast<jint>(pos)
            : static_cast<jint>(-1);
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_matVecNative(
    JNIEnv* env,
    jclass,
    jdoubleArray a,
    jdoubleArray x,
    jint m,
    jint n
) {
    if (!env || !a || !x || m <= 0 || n <= 0) {
        return env ? env->NewDoubleArray(0) : nullptr;
    }

    const jsize lenA = env->GetArrayLength(a);
    const jsize lenX = env->GetArrayLength(x);
    const jsize needA = static_cast<jsize>(m) * static_cast<jsize>(n);
    if (lenA < needA || lenX < n) return env->NewDoubleArray(0);

    jdouble* rawA = env->GetDoubleArrayElements(a, nullptr);
    jdouble* rawX = env->GetDoubleArrayElements(x, nullptr);
    if (!rawA || !rawX) {
        if (rawA) env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
        if (rawX) env->ReleaseDoubleArrayElements(x, rawX, JNI_ABORT);
        return env->NewDoubleArray(0);
    }

    std::vector<jdouble> out(static_cast<size_t>(m), 0.0);
    for (jint i = 0; i < m; ++i) {
        const jint aRow = i * n;
        double sum = 0.0;
        for (jint k = 0; k < n; ++k) {
            sum += static_cast<double>(rawA[aRow + k]) * static_cast<double>(rawX[k]);
        }
        out[static_cast<size_t>(i)] = static_cast<jdouble>(sum);
    }

    env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
    env->ReleaseDoubleArrayElements(x, rawX, JNI_ABORT);

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;
    env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_matMulBlockedNative(
    JNIEnv* env,
    jclass,
    jdoubleArray a,
    jdoubleArray b,
    jint m,
    jint n,
    jint p,
    jint blockSize
) {
    if (!env || !a || !b || m <= 0 || n <= 0 || p <= 0) {
        return env ? env->NewDoubleArray(0) : nullptr;
    }

    const jsize lenA = env->GetArrayLength(a);
    const jsize lenB = env->GetArrayLength(b);
    const jsize needA = static_cast<jsize>(m) * static_cast<jsize>(n);
    const jsize needB = static_cast<jsize>(n) * static_cast<jsize>(p);
    if (lenA < needA || lenB < needB) return env->NewDoubleArray(0);

    jdouble* rawA = env->GetDoubleArrayElements(a, nullptr);
    jdouble* rawB = env->GetDoubleArrayElements(b, nullptr);
    if (!rawA || !rawB) {
        if (rawA) env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
        if (rawB) env->ReleaseDoubleArrayElements(b, rawB, JNI_ABORT);
        return env->NewDoubleArray(0);
    }

    const jint bs = blockSize <= 0 ? 32 : blockSize;
    std::vector<jdouble> out(static_cast<size_t>(m) * static_cast<size_t>(p), 0.0);
    for (jint ii = 0; ii < m; ii += bs) {
        const jint iMax = (ii + bs < m) ? (ii + bs) : m;
        for (jint kk = 0; kk < n; kk += bs) {
            const jint kMax = (kk + bs < n) ? (kk + bs) : n;
            for (jint jj = 0; jj < p; jj += bs) {
                const jint jMax = (jj + bs < p) ? (jj + bs) : p;
                for (jint i = ii; i < iMax; ++i) {
                    const jint aRow = i * n;
                    const jint cRow = i * p;
                    for (jint k = kk; k < kMax; ++k) {
                        const double aval = rawA[aRow + k];
                        const jint bRow = k * p;
                        for (jint j = jj; j < jMax; ++j) {
                            out[static_cast<size_t>(cRow + j)] += aval * static_cast<double>(rawB[bRow + j]);
                        }
                    }
                }
            }
        }
    }

    env->ReleaseDoubleArrayElements(a, rawA, JNI_ABORT);
    env->ReleaseDoubleArrayElements(b, rawB, JNI_ABORT);

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;
    env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_conv2dNative(
    JNIEnv* env,
    jclass,
    jdoubleArray input,
    jint width,
    jint height,
    jdoubleArray kernel,
    jint kWidth,
    jint kHeight
) {
    if (!env || !input || !kernel || width <= 0 || height <= 0 || kWidth <= 0 || kHeight <= 0) {
        return env ? env->NewDoubleArray(0) : nullptr;
    }

    const jsize lenIn = env->GetArrayLength(input);
    const jsize lenKernel = env->GetArrayLength(kernel);
    const jsize needIn = static_cast<jsize>(width) * static_cast<jsize>(height);
    const jsize needKernel = static_cast<jsize>(kWidth) * static_cast<jsize>(kHeight);
    if (lenIn < needIn || lenKernel < needKernel) return env->NewDoubleArray(0);

    jdouble* rawIn = env->GetDoubleArrayElements(input, nullptr);
    jdouble* rawKernel = env->GetDoubleArrayElements(kernel, nullptr);
    if (!rawIn || !rawKernel) {
        if (rawIn) env->ReleaseDoubleArrayElements(input, rawIn, JNI_ABORT);
        if (rawKernel) env->ReleaseDoubleArrayElements(kernel, rawKernel, JNI_ABORT);
        return env->NewDoubleArray(0);
    }

    std::vector<jdouble> out(static_cast<size_t>(width) * static_cast<size_t>(height), 0.0);
    const jint kCenterX = kWidth / 2;
    const jint kCenterY = kHeight / 2;
    for (jint y = 0; y < height; ++y) {
        const jint row = y * width;
        for (jint x = 0; x < width; ++x) {
            double sum = 0.0;
            for (jint ky = 0; ky < kHeight; ++ky) {
                const jint iy = y + ky - kCenterY;
                if (iy < 0 || iy >= height) continue;
                const jint iRow = iy * width;
                const jint kRow = ky * kWidth;
                for (jint kx = 0; kx < kWidth; ++kx) {
                    const jint ix = x + kx - kCenterX;
                    if (ix < 0 || ix >= width) continue;
                    sum += static_cast<double>(rawIn[iRow + ix]) * static_cast<double>(rawKernel[kRow + kx]);
                }
            }
            out[static_cast<size_t>(row + x)] = static_cast<jdouble>(sum);
        }
    }

    env->ReleaseDoubleArrayElements(input, rawIn, JNI_ABORT);
    env->ReleaseDoubleArrayElements(kernel, rawKernel, JNI_ABORT);

    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(out.size()));
    if (!result) return nullptr;
    env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    return result;
}
