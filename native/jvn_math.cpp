#include <jni.h>
#include <vector>

extern "C" JNIEXPORT jdouble JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_dotProductNative(
    JNIEnv* env, jclass, jdoubleArray a, jdoubleArray b) {
  if (a == nullptr || b == nullptr) return 0.0;
  jsize lenA = env->GetArrayLength(a);
  jsize lenB = env->GetArrayLength(b);
  jsize n = lenA < lenB ? lenA : lenB;
  if (n <= 0) return 0.0;

  std::vector<jdouble> bufA(n);
  std::vector<jdouble> bufB(n);
  env->GetDoubleArrayRegion(a, 0, n, bufA.data());
  env->GetDoubleArrayRegion(b, 0, n, bufB.data());

  double sum = 0.0;
  for (jsize i = 0; i < n; ++i) {
    sum += bufA[i] * bufB[i];
  }
  return sum;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_matMulNative(
    JNIEnv* env, jclass, jdoubleArray a, jdoubleArray b, jint m, jint n, jint p) {
  if (a == nullptr || b == nullptr || m <= 0 || n <= 0 || p <= 0) {
    return env->NewDoubleArray(0);
  }
  jsize lenA = env->GetArrayLength(a);
  jsize lenB = env->GetArrayLength(b);
  jsize needA = m * n;
  jsize needB = n * p;
  if (lenA < needA || lenB < needB) {
    return env->NewDoubleArray(0);
  }

  std::vector<jdouble> bufA(needA);
  std::vector<jdouble> bufB(needB);
  env->GetDoubleArrayRegion(a, 0, needA, bufA.data());
  env->GetDoubleArrayRegion(b, 0, needB, bufB.data());

  std::vector<jdouble> out(m * p, 0.0);
  for (jint i = 0; i < m; ++i) {
    jint aRow = i * n;
    jint cRow = i * p;
    for (jint k = 0; k < n; ++k) {
      double aval = bufA[aRow + k];
      jint bRow = k * p;
      for (jint j = 0; j < p; ++j) {
        out[cRow + j] += aval * bufB[bRow + j];
      }
    }
  }

  jdoubleArray result = env->NewDoubleArray(m * p);
  if (result == nullptr) return env->NewDoubleArray(0);
  env->SetDoubleArrayRegion(result, 0, m * p, out.data());
  return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_matVecNative(
    JNIEnv* env, jclass, jdoubleArray a, jdoubleArray x, jint m, jint n) {
  if (a == nullptr || x == nullptr || m <= 0 || n <= 0) {
    return env->NewDoubleArray(0);
  }
  jsize lenA = env->GetArrayLength(a);
  jsize lenX = env->GetArrayLength(x);
  jsize needA = m * n;
  if (lenA < needA || lenX < n) {
    return env->NewDoubleArray(0);
  }

  std::vector<jdouble> bufA(needA);
  std::vector<jdouble> bufX(n);
  env->GetDoubleArrayRegion(a, 0, needA, bufA.data());
  env->GetDoubleArrayRegion(x, 0, n, bufX.data());

  std::vector<jdouble> out(m, 0.0);
  for (jint i = 0; i < m; ++i) {
    jint aRow = i * n;
    double sum = 0.0;
    for (jint k = 0; k < n; ++k) {
      sum += bufA[aRow + k] * bufX[k];
    }
    out[i] = sum;
  }

  jdoubleArray result = env->NewDoubleArray(m);
  if (result == nullptr) return env->NewDoubleArray(0);
  env->SetDoubleArrayRegion(result, 0, m, out.data());
  return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_matMulBlockedNative(
    JNIEnv* env, jclass, jdoubleArray a, jdoubleArray b,
    jint m, jint n, jint p, jint blockSize) {
  if (a == nullptr || b == nullptr || m <= 0 || n <= 0 || p <= 0) {
    return env->NewDoubleArray(0);
  }
  jsize lenA = env->GetArrayLength(a);
  jsize lenB = env->GetArrayLength(b);
  jsize needA = m * n;
  jsize needB = n * p;
  if (lenA < needA || lenB < needB) {
    return env->NewDoubleArray(0);
  }
  jint bs = blockSize <= 0 ? 32 : blockSize;

  std::vector<jdouble> bufA(needA);
  std::vector<jdouble> bufB(needB);
  env->GetDoubleArrayRegion(a, 0, needA, bufA.data());
  env->GetDoubleArrayRegion(b, 0, needB, bufB.data());

  std::vector<jdouble> out(m * p, 0.0);
  for (jint ii = 0; ii < m; ii += bs) {
    jint iMax = (ii + bs) < m ? (ii + bs) : m;
    for (jint kk = 0; kk < n; kk += bs) {
      jint kMax = (kk + bs) < n ? (kk + bs) : n;
      for (jint jj = 0; jj < p; jj += bs) {
        jint jMax = (jj + bs) < p ? (jj + bs) : p;
        for (jint i = ii; i < iMax; ++i) {
          jint aRow = i * n;
          jint cRow = i * p;
          for (jint k = kk; k < kMax; ++k) {
            double aval = bufA[aRow + k];
            jint bRow = k * p;
            for (jint j = jj; j < jMax; ++j) {
              out[cRow + j] += aval * bufB[bRow + j];
            }
          }
        }
      }
    }
  }

  jdoubleArray result = env->NewDoubleArray(m * p);
  if (result == nullptr) return env->NewDoubleArray(0);
  env->SetDoubleArrayRegion(result, 0, m * p, out.data());
  return result;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_jvn_core_nativebridge_NativeMathBridge_conv2dNative(
    JNIEnv* env, jclass, jdoubleArray input, jint width, jint height,
    jdoubleArray kernel, jint kWidth, jint kHeight) {
  if (input == nullptr || kernel == nullptr || width <= 0 || height <= 0 || kWidth <= 0 || kHeight <= 0) {
    return env->NewDoubleArray(0);
  }
  jsize lenIn = env->GetArrayLength(input);
  jsize lenK = env->GetArrayLength(kernel);
  jsize needIn = width * height;
  jsize needK = kWidth * kHeight;
  if (lenIn < needIn || lenK < needK) {
    return env->NewDoubleArray(0);
  }

  std::vector<jdouble> bufIn(needIn);
  std::vector<jdouble> bufK(needK);
  env->GetDoubleArrayRegion(input, 0, needIn, bufIn.data());
  env->GetDoubleArrayRegion(kernel, 0, needK, bufK.data());

  std::vector<jdouble> out(width * height, 0.0);
  jint kCenterX = kWidth / 2;
  jint kCenterY = kHeight / 2;
  for (jint y = 0; y < height; ++y) {
    jint row = y * width;
    for (jint x = 0; x < width; ++x) {
      double sum = 0.0;
      for (jint ky = 0; ky < kHeight; ++ky) {
        jint iy = y + ky - kCenterY;
        if (iy < 0 || iy >= height) continue;
        jint iRow = iy * width;
        jint kRow = ky * kWidth;
        for (jint kx = 0; kx < kWidth; ++kx) {
          jint ix = x + kx - kCenterX;
          if (ix < 0 || ix >= width) continue;
          sum += bufIn[iRow + ix] * bufK[kRow + kx];
        }
      }
      out[row + x] = sum;
    }
  }

  jdoubleArray result = env->NewDoubleArray(width * height);
  if (result == nullptr) return env->NewDoubleArray(0);
  env->SetDoubleArrayRegion(result, 0, width * height, out.data());
  return result;
}
