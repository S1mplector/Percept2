#pragma once
#include "Vector3D.hpp"
#include "Matrix4.hpp"
#include <cmath>

namespace ethereal {

struct Quaternion {
    float w = 1.0f;
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;

    Quaternion() = default;
    Quaternion(float w, float x, float y, float z);
    
    static Quaternion identity();
    static Quaternion fromAxisAngle(const Vector3D& axis, float angle);
    static Quaternion fromEuler(float pitch, float yaw, float roll);
    static Quaternion fromEuler(const Vector3D& euler);
    static Quaternion lookRotation(const Vector3D& forward, const Vector3D& up = Vector3D(0, 1, 0));

    Quaternion operator*(const Quaternion& other) const;
    Vector3D operator*(const Vector3D& v) const;
    Quaternion operator*(float s) const;
    Quaternion operator+(const Quaternion& other) const;

    float length() const;
    float lengthSquared() const;
    Quaternion normalized() const;
    Quaternion conjugate() const;
    Quaternion inverse() const;
    float dot(const Quaternion& other) const;

    Vector3D toEuler() const;
    Matrix4 toMatrix() const;
    void toAxisAngle(Vector3D& axis, float& angle) const;

    Vector3D forward() const;
    Vector3D right() const;
    Vector3D up() const;

    static Quaternion slerp(const Quaternion& a, const Quaternion& b, float t);
    static Quaternion lerp(const Quaternion& a, const Quaternion& b, float t);
};

} // namespace ethereal
