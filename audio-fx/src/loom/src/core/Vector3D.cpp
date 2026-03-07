#include "Vector3D.hpp"

namespace ethereal {

Vector3D::Vector3D(float x, float y, float z) : x(x), y(y), z(z) {}
Vector3D::Vector3D(const Vector2D& v2, float z) : x(v2.x), y(v2.y), z(z) {}

Vector3D Vector3D::operator+(const Vector3D& other) const {
    return Vector3D(x + other.x, y + other.y, z + other.z);
}

Vector3D Vector3D::operator-(const Vector3D& other) const {
    return Vector3D(x - other.x, y - other.y, z - other.z);
}

Vector3D Vector3D::operator*(float scalar) const {
    return Vector3D(x * scalar, y * scalar, z * scalar);
}

Vector3D Vector3D::operator/(float scalar) const {
    return Vector3D(x / scalar, y / scalar, z / scalar);
}

Vector3D& Vector3D::operator+=(const Vector3D& other) {
    x += other.x; y += other.y; z += other.z;
    return *this;
}

Vector3D& Vector3D::operator-=(const Vector3D& other) {
    x -= other.x; y -= other.y; z -= other.z;
    return *this;
}

Vector3D& Vector3D::operator*=(float scalar) {
    x *= scalar; y *= scalar; z *= scalar;
    return *this;
}

float Vector3D::length() const {
    return std::sqrt(x * x + y * y + z * z);
}

float Vector3D::lengthSquared() const {
    return x * x + y * y + z * z;
}

Vector3D Vector3D::normalized() const {
    float len = length();
    if (len > 0.0001f) return *this / len;
    return Vector3D::zero();
}

float Vector3D::dot(const Vector3D& other) const {
    return x * other.x + y * other.y + z * other.z;
}

Vector3D Vector3D::cross(const Vector3D& other) const {
    return Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    );
}

Vector3D Vector3D::lerp(const Vector3D& target, float t) const {
    return *this + (target - *this) * t;
}

Vector2D Vector3D::xy() const { return Vector2D(x, y); }
Vector2D Vector3D::xz() const { return Vector2D(x, z); }

Vector3D Vector3D::zero() { return Vector3D(0, 0, 0); }
Vector3D Vector3D::up() { return Vector3D(0, -1, 0); }
Vector3D Vector3D::forward() { return Vector3D(0, 0, 1); }

Vector3D operator*(float scalar, const Vector3D& v) { return v * scalar; }

} // namespace ethereal
