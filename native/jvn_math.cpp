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
