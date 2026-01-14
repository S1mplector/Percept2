package com.jvn.core.nativebridge;

public final class NativeMathBridge {
  private static final String LIB_NAME = "jvn_math";
  private static final boolean LOADED = NativeLibraryLoader.load(LIB_NAME);

  private NativeMathBridge() {}

  public static boolean isAvailable() { return LOADED; }

  public static double dotProduct(double[] a, double[] b) {
    if (!LOADED) return dotProductJava(a, b);
    try {
      return dotProductNative(a, b);
    } catch (UnsatisfiedLinkError e) {
      return dotProductJava(a, b);
    }
  }

  public static double[] matMul(double[] a, double[] b, int m, int n, int p) {
    if (!LOADED) return matMulJava(a, b, m, n, p);
    try {
      return matMulNative(a, b, m, n, p);
    } catch (UnsatisfiedLinkError e) {
      return matMulJava(a, b, m, n, p);
    }
  }

  private static native double dotProductNative(double[] a, double[] b);
  private static native double[] matMulNative(double[] a, double[] b, int m, int n, int p);

  private static double dotProductJava(double[] a, double[] b) {
    if (a == null || b == null) return 0.0;
    int n = Math.min(a.length, b.length);
    double sum = 0.0;
    for (int i = 0; i < n; i++) {
      sum += a[i] * b[i];
    }
    return sum;
  }

  private static double[] matMulJava(double[] a, double[] b, int m, int n, int p) {
    if (a == null || b == null || m <= 0 || n <= 0 || p <= 0) return new double[0];
    if (a.length < m * n || b.length < n * p) return new double[0];
    double[] out = new double[m * p];
    for (int i = 0; i < m; i++) {
      int aRow = i * n;
      int cRow = i * p;
      for (int k = 0; k < n; k++) {
        double aval = a[aRow + k];
        int bRow = k * p;
        for (int j = 0; j < p; j++) {
          out[cRow + j] += aval * b[bRow + j];
        }
      }
    }
    return out;
  }
}
