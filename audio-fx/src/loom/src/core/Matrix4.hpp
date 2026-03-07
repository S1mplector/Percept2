#pragma once
#include "Vector3D.hpp"
#include <array>
#include <cmath>

namespace ethereal {

struct Matrix4 {
    std::array<float, 16> m;

    Matrix4();
    
    static Matrix4 identity();
    static Matrix4 translation(float x, float y, float z);
    static Matrix4 translation(const Vector3D& v);
    static Matrix4 scale(float x, float y, float z);
    static Matrix4 scale(float s);
    static Matrix4 rotationX(float angle);
    static Matrix4 rotationY(float angle);
    static Matrix4 rotationZ(float angle);
    static Matrix4 rotation(const Vector3D& axis, float angle);
    static Matrix4 lookAt(const Vector3D& eye, const Vector3D& target, const Vector3D& up);
    static Matrix4 perspective(float fov, float aspect, float near, float far);
    static Matrix4 orthographic(float left, float right, float bottom, float top, float near, float far);

    Matrix4 operator*(const Matrix4& other) const;
    Vector3D operator*(const Vector3D& v) const;
    
    Matrix4 transposed() const;
    Matrix4 inverted() const;
    float determinant() const;

    Vector3D transformPoint(const Vector3D& p) const;
    Vector3D transformDirection(const Vector3D& d) const;

    float& at(int row, int col);
    float at(int row, int col) const;
};

} // namespace ethereal
