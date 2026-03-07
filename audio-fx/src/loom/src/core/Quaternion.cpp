#include "Quaternion.hpp"

namespace ethereal {

Quaternion::Quaternion(float w, float x, float y, float z) : w(w), x(x), y(y), z(z) {}

Quaternion Quaternion::identity() {
    return Quaternion(1, 0, 0, 0);
}

Quaternion Quaternion::fromAxisAngle(const Vector3D& axis, float angle) {
    Vector3D a = axis.normalized();
    float halfAngle = angle * 0.5f;
    float s = std::sin(halfAngle);
    return Quaternion(std::cos(halfAngle), a.x * s, a.y * s, a.z * s);
}

Quaternion Quaternion::fromEuler(float pitch, float yaw, float roll) {
    float cp = std::cos(pitch * 0.5f), sp = std::sin(pitch * 0.5f);
    float cy = std::cos(yaw * 0.5f), sy = std::sin(yaw * 0.5f);
    float cr = std::cos(roll * 0.5f), sr = std::sin(roll * 0.5f);

    return Quaternion(
        cr * cp * cy + sr * sp * sy,
        sr * cp * cy - cr * sp * sy,
        cr * sp * cy + sr * cp * sy,
        cr * cp * sy - sr * sp * cy
    );
}

Quaternion Quaternion::fromEuler(const Vector3D& euler) {
    return fromEuler(euler.x, euler.y, euler.z);
}

Quaternion Quaternion::lookRotation(const Vector3D& forward, const Vector3D& up) {
    Vector3D f = forward.normalized();
    Vector3D r = up.cross(f).normalized();
    Vector3D u = f.cross(r);

    float m00 = r.x, m01 = r.y, m02 = r.z;
    float m10 = u.x, m11 = u.y, m12 = u.z;
    float m20 = f.x, m21 = f.y, m22 = f.z;

    float trace = m00 + m11 + m22;
    Quaternion q;

    if (trace > 0) {
        float s = 0.5f / std::sqrt(trace + 1.0f);
        q.w = 0.25f / s;
        q.x = (m21 - m12) * s;
        q.y = (m02 - m20) * s;
        q.z = (m10 - m01) * s;
    } else if (m00 > m11 && m00 > m22) {
        float s = 2.0f * std::sqrt(1.0f + m00 - m11 - m22);
        q.w = (m21 - m12) / s;
        q.x = 0.25f * s;
        q.y = (m01 + m10) / s;
        q.z = (m02 + m20) / s;
    } else if (m11 > m22) {
        float s = 2.0f * std::sqrt(1.0f + m11 - m00 - m22);
        q.w = (m02 - m20) / s;
        q.x = (m01 + m10) / s;
        q.y = 0.25f * s;
        q.z = (m12 + m21) / s;
    } else {
        float s = 2.0f * std::sqrt(1.0f + m22 - m00 - m11);
        q.w = (m10 - m01) / s;
        q.x = (m02 + m20) / s;
        q.y = (m12 + m21) / s;
        q.z = 0.25f * s;
    }
    return q.normalized();
}

Quaternion Quaternion::operator*(const Quaternion& other) const {
    return Quaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w
    );
}

Vector3D Quaternion::operator*(const Vector3D& v) const {
    Vector3D qv(x, y, z);
    Vector3D uv = qv.cross(v);
    Vector3D uuv = qv.cross(uv);
    return v + ((uv * w) + uuv) * 2.0f;
}

Quaternion Quaternion::operator*(float s) const {
    return Quaternion(w * s, x * s, y * s, z * s);
}

Quaternion Quaternion::operator+(const Quaternion& other) const {
    return Quaternion(w + other.w, x + other.x, y + other.y, z + other.z);
}

float Quaternion::length() const {
    return std::sqrt(w * w + x * x + y * y + z * z);
}

float Quaternion::lengthSquared() const {
    return w * w + x * x + y * y + z * z;
}

Quaternion Quaternion::normalized() const {
    float len = length();
    if (len < 0.0001f) return identity();
    float inv = 1.0f / len;
    return Quaternion(w * inv, x * inv, y * inv, z * inv);
}

Quaternion Quaternion::conjugate() const {
    return Quaternion(w, -x, -y, -z);
}

Quaternion Quaternion::inverse() const {
    float lenSq = lengthSquared();
    if (lenSq < 0.0001f) return identity();
    float inv = 1.0f / lenSq;
    return Quaternion(w * inv, -x * inv, -y * inv, -z * inv);
}

float Quaternion::dot(const Quaternion& other) const {
    return w * other.w + x * other.x + y * other.y + z * other.z;
}

Vector3D Quaternion::toEuler() const {
    Vector3D euler;
    
    float sinp = 2.0f * (w * y - z * x);
    if (std::abs(sinp) >= 1.0f)
        euler.x = std::copysign(3.14159f / 2.0f, sinp);
    else
        euler.x = std::asin(sinp);

    float siny_cosp = 2.0f * (w * z + x * y);
    float cosy_cosp = 1.0f - 2.0f * (y * y + z * z);
    euler.y = std::atan2(siny_cosp, cosy_cosp);

    float sinr_cosp = 2.0f * (w * x + y * z);
    float cosr_cosp = 1.0f - 2.0f * (x * x + y * y);
    euler.z = std::atan2(sinr_cosp, cosr_cosp);

    return euler;
}

Matrix4 Quaternion::toMatrix() const {
    Matrix4 m = Matrix4::identity();
    
    float xx = x * x, yy = y * y, zz = z * z;
    float xy = x * y, xz = x * z, yz = y * z;
    float wx = w * x, wy = w * y, wz = w * z;

    m.m[0] = 1.0f - 2.0f * (yy + zz);
    m.m[1] = 2.0f * (xy + wz);
    m.m[2] = 2.0f * (xz - wy);

    m.m[4] = 2.0f * (xy - wz);
    m.m[5] = 1.0f - 2.0f * (xx + zz);
    m.m[6] = 2.0f * (yz + wx);

    m.m[8] = 2.0f * (xz + wy);
    m.m[9] = 2.0f * (yz - wx);
    m.m[10] = 1.0f - 2.0f * (xx + yy);

    return m;
}

void Quaternion::toAxisAngle(Vector3D& axis, float& angle) const {
    Quaternion q = normalized();
    angle = 2.0f * std::acos(q.w);
    float s = std::sqrt(1.0f - q.w * q.w);
    if (s < 0.001f) {
        axis = Vector3D(1, 0, 0);
    } else {
        axis = Vector3D(q.x / s, q.y / s, q.z / s);
    }
}

Vector3D Quaternion::forward() const { return *this * Vector3D(0, 0, 1); }
Vector3D Quaternion::right() const { return *this * Vector3D(1, 0, 0); }
Vector3D Quaternion::up() const { return *this * Vector3D(0, 1, 0); }

Quaternion Quaternion::slerp(const Quaternion& a, const Quaternion& b, float t) {
    Quaternion q2 = b;
    float d = a.dot(b);
    
    if (d < 0.0f) {
        q2 = Quaternion(-b.w, -b.x, -b.y, -b.z);
        d = -d;
    }

    if (d > 0.9995f) {
        return lerp(a, q2, t);
    }

    float theta = std::acos(d);
    float sinTheta = std::sin(theta);
    float wa = std::sin((1.0f - t) * theta) / sinTheta;
    float wb = std::sin(t * theta) / sinTheta;

    return Quaternion(
        wa * a.w + wb * q2.w,
        wa * a.x + wb * q2.x,
        wa * a.y + wb * q2.y,
        wa * a.z + wb * q2.z
    );
}

Quaternion Quaternion::lerp(const Quaternion& a, const Quaternion& b, float t) {
    return (a * (1.0f - t) + b * t).normalized();
}

} // namespace ethereal
