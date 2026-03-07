#include "Vector2D.hpp"

namespace ethereal {

Vector2D::Vector2D(float x, float y) : x(x), y(y) {}

Vector2D Vector2D::operator+(const Vector2D& other) const {
    return Vector2D(x + other.x, y + other.y);
}

Vector2D Vector2D::operator-(const Vector2D& other) const {
    return Vector2D(x - other.x, y - other.y);
}

Vector2D Vector2D::operator*(float scalar) const {
    return Vector2D(x * scalar, y * scalar);
}

Vector2D Vector2D::operator/(float scalar) const {
    return Vector2D(x / scalar, y / scalar);
}

Vector2D& Vector2D::operator+=(const Vector2D& other) {
    x += other.x;
    y += other.y;
    return *this;
}

Vector2D& Vector2D::operator-=(const Vector2D& other) {
    x -= other.x;
    y -= other.y;
    return *this;
}

Vector2D& Vector2D::operator*=(float scalar) {
    x *= scalar;
    y *= scalar;
    return *this;
}

Vector2D& Vector2D::operator/=(float scalar) {
    x /= scalar;
    y /= scalar;
    return *this;
}

float Vector2D::length() const {
    return std::sqrt(x * x + y * y);
}

float Vector2D::lengthSquared() const {
    return x * x + y * y;
}

Vector2D Vector2D::normalized() const {
    float len = length();
    if (len > 0.0001f) {
        return *this / len;
    }
    return Vector2D::zero();
}

float Vector2D::dot(const Vector2D& other) const {
    return x * other.x + y * other.y;
}

float Vector2D::cross(const Vector2D& other) const {
    return x * other.y - y * other.x;
}

Vector2D Vector2D::perpendicular() const {
    return Vector2D(-y, x);
}

Vector2D Vector2D::lerp(const Vector2D& target, float t) const {
    return *this + (target - *this) * t;
}

float Vector2D::angleTo(const Vector2D& other) const {
    return std::atan2(cross(other), dot(other));
}

Vector2D Vector2D::rotated(float angle) const {
    float c = std::cos(angle);
    float s = std::sin(angle);
    return Vector2D(x * c - y * s, x * s + y * c);
}

Vector2D Vector2D::fromAngle(float angle, float length) {
    return Vector2D(std::cos(angle) * length, std::sin(angle) * length);
}

Vector2D Vector2D::zero() { return Vector2D(0.0f, 0.0f); }
Vector2D Vector2D::up() { return Vector2D(0.0f, -1.0f); }
Vector2D Vector2D::down() { return Vector2D(0.0f, 1.0f); }
Vector2D Vector2D::left() { return Vector2D(-1.0f, 0.0f); }
Vector2D Vector2D::right() { return Vector2D(1.0f, 0.0f); }

Vector2D operator*(float scalar, const Vector2D& v) {
    return v * scalar;
}

} // namespace ethereal
