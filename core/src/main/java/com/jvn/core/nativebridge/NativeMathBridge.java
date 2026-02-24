package com.jvn.core.nativebridge;

public final class NativeMathBridge {
  private static final String LIB_NAME = "jvn_native_bridge";
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

  public static double[] matVec(double[] a, double[] x, int m, int n) {
    if (!LOADED) return matVecJava(a, x, m, n);
    try {
      return matVecNative(a, x, m, n);
    } catch (UnsatisfiedLinkError e) {
      return matVecJava(a, x, m, n);
    }
  }

  public static double[] matMulBlocked(double[] a, double[] b, int m, int n, int p, int blockSize) {
    if (!LOADED) return matMulBlockedJava(a, b, m, n, p, blockSize);
    try {
      return matMulBlockedNative(a, b, m, n, p, blockSize);
    } catch (UnsatisfiedLinkError e) {
      return matMulBlockedJava(a, b, m, n, p, blockSize);
    }
  }

  public static double[] conv2d(double[] input, int width, int height,
                                double[] kernel, int kWidth, int kHeight) {
    if (!LOADED) return conv2dJava(input, width, height, kernel, kWidth, kHeight);
    try {
      return conv2dNative(input, width, height, kernel, kWidth, kHeight);
    } catch (UnsatisfiedLinkError e) {
      return conv2dJava(input, width, height, kernel, kWidth, kHeight);
    }
  }

  private static native double dotProductNative(double[] a, double[] b);
  private static native double[] matMulNative(double[] a, double[] b, int m, int n, int p);
  private static native double[] matVecNative(double[] a, double[] x, int m, int n);
  private static native double[] matMulBlockedNative(double[] a, double[] b, int m, int n, int p, int blockSize);
  private static native double[] conv2dNative(double[] input, int width, int height,
                                              double[] kernel, int kWidth, int kHeight);

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

  private static double[] matVecJava(double[] a, double[] x, int m, int n) {
    if (a == null || x == null || m <= 0 || n <= 0) return new double[0];
    if (a.length < m * n || x.length < n) return new double[0];
    double[] out = new double[m];
    for (int i = 0; i < m; i++) {
      int aRow = i * n;
      double sum = 0.0;
      for (int k = 0; k < n; k++) {
        sum += a[aRow + k] * x[k];
      }
      out[i] = sum;
    }
    return out;
  }

  private static double[] matMulBlockedJava(double[] a, double[] b, int m, int n, int p, int blockSize) {
    if (a == null || b == null || m <= 0 || n <= 0 || p <= 0) return new double[0];
    if (a.length < m * n || b.length < n * p) return new double[0];
    int bs = blockSize <= 0 ? 32 : blockSize;
    double[] out = new double[m * p];
    for (int ii = 0; ii < m; ii += bs) {
      int iMax = Math.min(m, ii + bs);
      for (int kk = 0; kk < n; kk += bs) {
        int kMax = Math.min(n, kk + bs);
        for (int jj = 0; jj < p; jj += bs) {
          int jMax = Math.min(p, jj + bs);
          for (int i = ii; i < iMax; i++) {
            int aRow = i * n;
            int cRow = i * p;
            for (int k = kk; k < kMax; k++) {
              double aval = a[aRow + k];
              int bRow = k * p;
              for (int j = jj; j < jMax; j++) {
                out[cRow + j] += aval * b[bRow + j];
              }
            }
          }
        }
      }
    }
    return out;
  }

  private static double[] conv2dJava(double[] input, int width, int height,
                                     double[] kernel, int kWidth, int kHeight) {
    if (input == null || kernel == null || width <= 0 || height <= 0 || kWidth <= 0 || kHeight <= 0) {
      return new double[0];
    }
    if (input.length < width * height || kernel.length < kWidth * kHeight) return new double[0];
    double[] out = new double[width * height];
    int kCenterX = kWidth / 2;
    int kCenterY = kHeight / 2;
    for (int y = 0; y < height; y++) {
      int row = y * width;
      for (int x = 0; x < width; x++) {
        double sum = 0.0;
        for (int ky = 0; ky < kHeight; ky++) {
          int iy = y + ky - kCenterY;
          if (iy < 0 || iy >= height) continue;
          int iRow = iy * width;
          int kRow = ky * kWidth;
          for (int kx = 0; kx < kWidth; kx++) {
            int ix = x + kx - kCenterX;
            if (ix < 0 || ix >= width) continue;
            sum += input[iRow + ix] * kernel[kRow + kx];
          }
        }
        out[row + x] = sum;
      }
    }
    return out;
  }
}
