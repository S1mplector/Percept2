package com.jvn.core3d.math;

/**
 * 3D vector with float precision
 */
public class Vector3f {
    public float x, y, z;

    public static final Vector3f ZERO = new Vector3f(0, 0, 0);
    public static final Vector3f ONE = new Vector3f(1, 1, 1);
    public static final Vector3f UP = new Vector3f(0, 1, 0);
    public static final Vector3f DOWN = new Vector3f(0, -1, 0);
    public static final Vector3f LEFT = new Vector3f(-1, 0, 0);
    public static final Vector3f RIGHT = new Vector3f(1, 0, 0);
    public static final Vector3f FORWARD = new Vector3f(0, 0, -1);
    public static final Vector3f BACK = new Vector3f(0, 0, 1);

    public Vector3f() {
        this(0, 0, 0);
    }

    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3f(Vector3f other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    // Arithmetic operations
    public Vector3f add(Vector3f other) {
        return new Vector3f(x + other.x, y + other.y, z + other.z);
    }

    public Vector3f subtract(Vector3f other) {
        return new Vector3f(x - other.x, y - other.y, z - other.z);
    }

    public Vector3f multiply(float scalar) {
        return new Vector3f(x * scalar, y * scalar, z * scalar);
    }

    public Vector3f multiply(Vector3f other) {
        return new Vector3f(x * other.x, y * other.y, z * other.z);
    }

    public Vector3f divide(float scalar) {
        if (Math.abs(scalar) < 1e-6f) {
            throw new ArithmeticException("Division by zero");
        }
        return new Vector3f(x / scalar, y / scalar, z / scalar);
    }

    public Vector3f negate() {
        return new Vector3f(-x, -y, -z);
    }

    // Vector operations
    public float dot(Vector3f other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vector3f cross(Vector3f other) {
        return new Vector3f(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public Vector3f normalize() {
        float len = length();
        if (len < 1e-6f) {
            return new Vector3f(0, 0, 0);
        }
        return divide(len);
    }

    public float distance(Vector3f other) {
        return subtract(other).length();
    }

    public float distanceSquared(Vector3f other) {
        return subtract(other).lengthSquared();
    }

    // Interpolation
    public Vector3f lerp(Vector3f target, float t) {
        t = Math.max(0, Math.min(1, t)); // Clamp to [0, 1]
        return new Vector3f(
            x + (target.x - x) * t,
            y + (target.y - y) * t,
            z + (target.z - z) * t
        );
    }

    // Utility
    public Vector3f reflect(Vector3f normal) {
        float dot = this.dot(normal);
        return subtract(normal.multiply(2 * dot));
    }

    public Vector3f project(Vector3f onto) {
        float dot = this.dot(onto);
        float lenSq = onto.lengthSquared();
        if (lenSq < 1e-6f) return new Vector3f(0, 0, 0);
        return onto.multiply(dot / lenSq);
    }

    // Mutable operations (for performance)
    public Vector3f set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3f set(Vector3f other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }

    public Vector3f addLocal(Vector3f other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    public Vector3f multiplyLocal(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
        return this;
    }

    public Vector3f normalizeLocal() {
        float len = length();
        if (len > 1e-6f) {
            this.x /= len;
            this.y /= len;
            this.z /= len;
        }
        return this;
    }

    // Comparison
    public boolean equals(Vector3f other, float epsilon) {
        if (other == null) return false;
        return Math.abs(x - other.x) < epsilon &&
               Math.abs(y - other.y) < epsilon &&
               Math.abs(z - other.z) < epsilon;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector3f)) return false;
        Vector3f other = (Vector3f) obj;
        return equals(other, 1e-6f);
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(x);
        result = 31 * result + Float.floatToIntBits(y);
        result = 31 * result + Float.floatToIntBits(z);
        return result;
    }

    @Override
    public String toString() {
        return String.format("Vector3f(%.3f, %.3f, %.3f)", x, y, z);
    }

    // Static utility methods
    public static float angle(Vector3f from, Vector3f to) {
        float dot = from.normalize().dot(to.normalize());
        dot = Math.max(-1, Math.min(1, dot)); // Clamp for acos
        return (float) Math.acos(dot);
    }

    public static Vector3f min(Vector3f a, Vector3f b) {
        return new Vector3f(
            Math.min(a.x, b.x),
            Math.min(a.y, b.y),
            Math.min(a.z, b.z)
        );
    }

    public static Vector3f max(Vector3f a, Vector3f b) {
        return new Vector3f(
            Math.max(a.x, b.x),
            Math.max(a.y, b.y),
            Math.max(a.z, b.z)
        );
    }
}
