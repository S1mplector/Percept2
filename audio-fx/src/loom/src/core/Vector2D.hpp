#pragma once
#include <cmath>

namespace ethereal {

struct Vector2D {
    float x = 0.0f;
    float y = 0.0f;

    Vector2D() = default;
    Vector2D(float x, float y);

    Vector2D operator+(const Vector2D& other) const;
    Vector2D operator-(const Vector2D& other) const;
    Vector2D operator*(float scalar) const;
    Vector2D operator/(float scalar) const;

    Vector2D& operator+=(const Vector2D& other);
    Vector2D& operator-=(const Vector2D& other);
    Vector2D& operator*=(float scalar);
    Vector2D& operator/=(float scalar);

    float length() const;
    float lengthSquared() const;
    Vector2D normalized() const;
    float dot(const Vector2D& other) const;
    float cross(const Vector2D& other) const;
    
    Vector2D perpendicular() const;
    Vector2D lerp(const Vector2D& target, float t) const;
    float angleTo(const Vector2D& other) const;
    Vector2D rotated(float angle) const;

    static Vector2D fromAngle(float angle, float length = 1.0f);
    static Vector2D zero();
    static Vector2D up();
    static Vector2D down();
    static Vector2D left();
    static Vector2D right();
};

Vector2D operator*(float scalar, const Vector2D& v);

} // namespace ethereal
