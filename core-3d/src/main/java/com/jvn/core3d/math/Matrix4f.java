package com.jvn.core3d.math;

public class Matrix4f {
    public float[] m = new float[16];

    public Matrix4f() { setIdentity(); }

    public static Matrix4f identity() { return new Matrix4f(); }

    public Matrix4f setIdentity() {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1f;
        return this;
    }

    public static Matrix4f multiply(Matrix4f a, Matrix4f b) {
        Matrix4f r = new Matrix4f();
        float[] am = a.m, bm = b.m, rm = r.m;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                rm[col + row * 4] =
                    am[0 + row * 4] * bm[col + 0] +
                    am[1 + row * 4] * bm[col + 4] +
                    am[2 + row * 4] * bm[col + 8] +
                    am[3 + row * 4] * bm[col + 12];
            }
        }
        return r;
    }

    public Matrix4f mul(Matrix4f other) { return multiply(this, other); }

    public static Matrix4f translation(float x, float y, float z) {
        Matrix4f r = identity();
        r.m[12] = x; r.m[13] = y; r.m[14] = z;
        return r;
    }

    public static Matrix4f scale(float x, float y, float z) {
        Matrix4f r = new Matrix4f();
        r.m[0] = x; r.m[5] = y; r.m[10] = z; r.m[15] = 1f;
        return r;
    }

    public static Matrix4f rotation(Quaternionf q) {
        Matrix4f r = new Matrix4f();
        float x = q.x, y = q.y, z = q.z, w = q.w;
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;
        r.m[0] = 1 - 2 * (yy + zz);
        r.m[1] = 2 * (xy + wz);
        r.m[2] = 2 * (xz - wy);
        r.m[3] = 0;
        r.m[4] = 2 * (xy - wz);
        r.m[5] = 1 - 2 * (xx + zz);
        r.m[6] = 2 * (yz + wx);
        r.m[7] = 0;
        r.m[8] = 2 * (xz + wy);
        r.m[9] = 2 * (yz - wx);
        r.m[10] = 1 - 2 * (xx + yy);
        r.m[11] = 0;
        r.m[12] = 0; r.m[13] = 0; r.m[14] = 0; r.m[15] = 1;
        return r;
    }

    public static Matrix4f perspective(float fovYRadians, float aspect, float zNear, float zFar) {
        float f = 1.0f / (float)Math.tan(fovYRadians / 2f);
        Matrix4f r = new Matrix4f();
        r.m[0] = f / aspect;
        r.m[5] = f;
        r.m[10] = (zFar + zNear) / (zNear - zFar);
        r.m[11] = -1f;
        r.m[14] = (2f * zFar * zNear) / (zNear - zFar);
        r.m[15] = 0f;
        return r;
    }

    public static Matrix4f lookAt(Vector3f eye, Vector3f center, Vector3f up) {
        Vector3f f = center.subtract(eye).normalize();
        Vector3f s = f.cross(up.normalize()).normalize();
        Vector3f u = s.cross(f);
        Matrix4f r = new Matrix4f();
        r.m[0] = s.x; r.m[4] = s.y; r.m[8] = s.z; r.m[12] = -s.dot(eye);
        r.m[1] = u.x; r.m[5] = u.y; r.m[9] = u.z; r.m[13] = -u.dot(eye);
        r.m[2] = -f.x; r.m[6] = -f.y; r.m[10] = -f.z; r.m[14] = f.dot(eye);
        r.m[3] = 0; r.m[7] = 0; r.m[11] = 0; r.m[15] = 1;
        return r;
    }

    public float[] toArray() { return m.clone(); }

    public static Matrix4f compose(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        Matrix4f t = translation(translation.x, translation.y, translation.z);
        Matrix4f r = rotation(rotation);
        Matrix4f s = scale(scale.x, scale.y, scale.z);
        return multiply(multiply(t, r), s);
    }
}
