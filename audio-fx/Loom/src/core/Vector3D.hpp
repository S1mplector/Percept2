#pragma once
#include <cmath>
#include "Vector2D.hpp"

namespace ethereal {

struct Vector3D {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;

    Vector3D() = default;
    Vector3D(float x, float y, float z);
    Vector3D(const Vector2D& v2, float z = 0.0f);

    Vector3D operator+(const Vector3D& other) const;
    Vector3D operator-(const Vector3D& other) const;
    Vector3D operator*(float scalar) const;
    Vector3D operator/(float scalar) const;

    Vector3D& operator+=(const Vector3D& other);
    Vector3D& operator-=(const Vector3D& other);
    Vector3D& operator*=(float scalar);

    float length() const;
    float lengthSquared() const;
    Vector3D normalized() const;
    float dot(const Vector3D& other) const;
    Vector3D cross(const Vector3D& other) const;
    Vector3D lerp(const Vector3D& target, float t) const;

    Vector2D xy() const;
    Vector2D xz() const;

    static Vector3D zero();
    static Vector3D up();
    static Vector3D forward();
};

Vector3D operator*(float scalar, const Vector3D& v);

} // namespace ethereal
