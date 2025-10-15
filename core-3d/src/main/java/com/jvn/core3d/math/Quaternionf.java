package com.jvn.core3d.math;

/**
 * Quaternion with float precision (x, y, z, w)
 */
public class Quaternionf {
    public float x, y, z, w;

    public static final Quaternionf IDENTITY = new Quaternionf(0, 0, 0, 1);

    public Quaternionf() { this(0, 0, 0, 1); }
    public Quaternionf(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w;
    }

    public Quaternionf(Quaternionf other) {
        this.x = other.x; this.y = other.y; this.z = other.z; this.w = other.w;
    }

    public static Quaternionf fromAxisAngle(Vector3f axis, float radians) {
        Vector3f n = axis.normalize();
        float s = (float)Math.sin(radians / 2f);
        float c = (float)Math.cos(radians / 2f);
        return new Quaternionf(n.x * s, n.y * s, n.z * s, c).normalize();
    }

    public static Quaternionf fromEulerRadians(float pitch, float yaw, float roll) {
        // Intrinsic XYZ rotation: pitch (X), yaw (Y), roll (Z)
        float cx = (float)Math.cos(pitch * 0.5f);
        float sx = (float)Math.sin(pitch * 0.5f);
        float cy = (float)Math.cos(yaw * 0.5f);
        float sy = (float)Math.sin(yaw * 0.5f);
        float cz = (float)Math.cos(roll * 0.5f);
        float sz = (float)Math.sin(roll * 0.5f);
        float w = cx*cy*cz + sx*sy*sz;
        float x = sx*cy*cz - cx*sy*sz;
        float y = cx*sy*cz + sx*cy*sz;
        float z = cx*cy*sz - sx*sy*cz;
        return new Quaternionf(x, y, z, w).normalize();
    }

    public static Quaternionf fromEulerDegrees(float pitchDeg, float yawDeg, float rollDeg) {
        float d2r = (float)Math.PI / 180f;
        return fromEulerRadians(pitchDeg * d2r, yawDeg * d2r, rollDeg * d2r);
    }

    public Quaternionf multiply(Quaternionf b) {
        Quaternionf a = this;
        float nx = a.w*b.x + a.x*b.w + a.y*b.z - a.z*b.y;
        float ny = a.w*b.y - a.x*b.z + a.y*b.w + a.z*b.x;
        float nz = a.w*b.z + a.x*b.y - a.y*b.x + a.z*b.w;
        float nw = a.w*b.w - a.x*b.x - a.y*b.y - a.z*b.z;
        return new Quaternionf(nx, ny, nz, nw);
    }

    public Quaternionf normalize() {
        float len = (float)Math.sqrt(x*x + y*y + z*z + w*w);
        if (len < 1e-8f) return new Quaternionf(0,0,0,1);
        return new Quaternionf(x/len, y/len, z/len, w/len);
    }

    public Quaternionf conjugate() { return new Quaternionf(-x, -y, -z, w); }

    public Vector3f transform(Vector3f v) {
        // Rotate vector by quaternion: v' = q * (v,0) * q^{-1}
        Quaternionf qv = new Quaternionf(v.x, v.y, v.z, 0);
        Quaternionf inv = conjugate();
        Quaternionf r = this.multiply(qv).multiply(inv);
        return new Vector3f(r.x, r.y, r.z);
    }

    public static Quaternionf slerp(Quaternionf a, Quaternionf b, float t) {
        t = Math.max(0, Math.min(1, t));
        float dot = a.x*b.x + a.y*b.y + a.z*b.z + a.w*b.w;
        Quaternionf bb = dot < 0 ? new Quaternionf(-b.x, -b.y, -b.z, -b.w) : b;
        if (dot < 0) dot = -dot;
        if (dot > 0.9995f) {
            // Linear interpolation fallback
            Quaternionf res = new Quaternionf(
                a.x + t*(bb.x - a.x),
                a.y + t*(bb.y - a.y),
                a.z + t*(bb.z - a.z),
                a.w + t*(bb.w - a.w)
            );
            return res.normalize();
        }
        double theta0 = Math.acos(dot);
        double theta = theta0 * t;
        double sinTheta = Math.sin(theta);
        double sinTheta0 = Math.sin(theta0);
        float s0 = (float)(Math.cos(theta) - dot * sinTheta / sinTheta0);
        float s1 = (float)(sinTheta / sinTheta0);
        return new Quaternionf(
            a.x*s0 + bb.x*s1,
            a.y*s0 + bb.y*s1,
            a.z*s0 + bb.z*s1,
            a.w*s0 + bb.w*s1
        );
    }

    @Override public String toString() {
        return String.format("Quaternionf(%.3f, %.3f, %.3f, %.3f)", x, y, z, w);
    }
}
