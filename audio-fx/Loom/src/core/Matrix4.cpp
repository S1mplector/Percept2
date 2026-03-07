#include "Matrix4.hpp"

namespace ethereal {

Matrix4::Matrix4() {
    m.fill(0.0f);
}

Matrix4 Matrix4::identity() {
    Matrix4 result;
    result.m[0] = result.m[5] = result.m[10] = result.m[15] = 1.0f;
    return result;
}

Matrix4 Matrix4::translation(float x, float y, float z) {
    Matrix4 result = identity();
    result.m[12] = x;
    result.m[13] = y;
    result.m[14] = z;
    return result;
}

Matrix4 Matrix4::translation(const Vector3D& v) {
    return translation(v.x, v.y, v.z);
}

Matrix4 Matrix4::scale(float x, float y, float z) {
    Matrix4 result = identity();
    result.m[0] = x;
    result.m[5] = y;
    result.m[10] = z;
    return result;
}

Matrix4 Matrix4::scale(float s) {
    return scale(s, s, s);
}

Matrix4 Matrix4::rotationX(float angle) {
    Matrix4 result = identity();
    float c = std::cos(angle);
    float s = std::sin(angle);
    result.m[5] = c;
    result.m[6] = s;
    result.m[9] = -s;
    result.m[10] = c;
    return result;
}

Matrix4 Matrix4::rotationY(float angle) {
    Matrix4 result = identity();
    float c = std::cos(angle);
    float s = std::sin(angle);
    result.m[0] = c;
    result.m[2] = -s;
    result.m[8] = s;
    result.m[10] = c;
    return result;
}

Matrix4 Matrix4::rotationZ(float angle) {
    Matrix4 result = identity();
    float c = std::cos(angle);
    float s = std::sin(angle);
    result.m[0] = c;
    result.m[1] = s;
    result.m[4] = -s;
    result.m[5] = c;
    return result;
}

Matrix4 Matrix4::rotation(const Vector3D& axis, float angle) {
    Matrix4 result = identity();
    Vector3D a = axis.normalized();
    float c = std::cos(angle);
    float s = std::sin(angle);
    float t = 1.0f - c;

    result.m[0] = t * a.x * a.x + c;
    result.m[1] = t * a.x * a.y + s * a.z;
    result.m[2] = t * a.x * a.z - s * a.y;

    result.m[4] = t * a.x * a.y - s * a.z;
    result.m[5] = t * a.y * a.y + c;
    result.m[6] = t * a.y * a.z + s * a.x;

    result.m[8] = t * a.x * a.z + s * a.y;
    result.m[9] = t * a.y * a.z - s * a.x;
    result.m[10] = t * a.z * a.z + c;

    return result;
}

Matrix4 Matrix4::lookAt(const Vector3D& eye, const Vector3D& target, const Vector3D& up) {
    Vector3D f = (target - eye).normalized();
    Vector3D r = f.cross(up).normalized();
    Vector3D u = r.cross(f);

    Matrix4 result = identity();
    result.m[0] = r.x;
    result.m[4] = r.y;
    result.m[8] = r.z;
    result.m[1] = u.x;
    result.m[5] = u.y;
    result.m[9] = u.z;
    result.m[2] = -f.x;
    result.m[6] = -f.y;
    result.m[10] = -f.z;
    result.m[12] = -r.dot(eye);
    result.m[13] = -u.dot(eye);
    result.m[14] = f.dot(eye);
    return result;
}

Matrix4 Matrix4::perspective(float fov, float aspect, float near, float far) {
    Matrix4 result;
    float tanHalf = std::tan(fov * 0.5f);
    result.m[0] = 1.0f / (aspect * tanHalf);
    result.m[5] = 1.0f / tanHalf;
    result.m[10] = -(far + near) / (far - near);
    result.m[11] = -1.0f;
    result.m[14] = -(2.0f * far * near) / (far - near);
    return result;
}

Matrix4 Matrix4::orthographic(float left, float right, float bottom, float top, float near, float far) {
    Matrix4 result = identity();
    result.m[0] = 2.0f / (right - left);
    result.m[5] = 2.0f / (top - bottom);
    result.m[10] = -2.0f / (far - near);
    result.m[12] = -(right + left) / (right - left);
    result.m[13] = -(top + bottom) / (top - bottom);
    result.m[14] = -(far + near) / (far - near);
    return result;
}

Matrix4 Matrix4::operator*(const Matrix4& other) const {
    Matrix4 result;
    for (int row = 0; row < 4; ++row) {
        for (int col = 0; col < 4; ++col) {
            result.m[col * 4 + row] = 
                m[0 * 4 + row] * other.m[col * 4 + 0] +
                m[1 * 4 + row] * other.m[col * 4 + 1] +
                m[2 * 4 + row] * other.m[col * 4 + 2] +
                m[3 * 4 + row] * other.m[col * 4 + 3];
        }
    }
    return result;
}

Vector3D Matrix4::operator*(const Vector3D& v) const {
    return transformPoint(v);
}

Matrix4 Matrix4::transposed() const {
    Matrix4 result;
    for (int i = 0; i < 4; ++i) {
        for (int j = 0; j < 4; ++j) {
            result.m[j * 4 + i] = m[i * 4 + j];
        }
    }
    return result;
}

float Matrix4::determinant() const {
    float a = m[0], b = m[1], c = m[2], d = m[3];
    float e = m[4], f = m[5], g = m[6], h = m[7];
    float i = m[8], j = m[9], k = m[10], l = m[11];
    float n = m[12], o = m[13], p = m[14], q = m[15];

    return a * (f * (k * q - l * p) - g * (j * q - l * o) + h * (j * p - k * o))
         - b * (e * (k * q - l * p) - g * (i * q - l * n) + h * (i * p - k * n))
         + c * (e * (j * q - l * o) - f * (i * q - l * n) + h * (i * o - j * n))
         - d * (e * (j * p - k * o) - f * (i * p - k * n) + g * (i * o - j * n));
}

Matrix4 Matrix4::inverted() const {
    Matrix4 inv;
    float det;

    inv.m[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
    inv.m[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
    inv.m[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
    inv.m[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
    inv.m[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
    inv.m[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
    inv.m[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
    inv.m[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
    inv.m[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
    inv.m[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
    inv.m[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
    inv.m[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
    inv.m[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
    inv.m[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
    inv.m[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
    inv.m[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

    det = m[0] * inv.m[0] + m[1] * inv.m[4] + m[2] * inv.m[8] + m[3] * inv.m[12];

    if (std::abs(det) < 0.0001f) return identity();

    det = 1.0f / det;
    for (int i = 0; i < 16; i++) inv.m[i] *= det;

    return inv;
}

Vector3D Matrix4::transformPoint(const Vector3D& p) const {
    float w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15];
    if (std::abs(w) < 0.0001f) w = 1.0f;
    return Vector3D(
        (m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]) / w,
        (m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]) / w,
        (m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]) / w
    );
}

Vector3D Matrix4::transformDirection(const Vector3D& d) const {
    return Vector3D(
        m[0] * d.x + m[4] * d.y + m[8] * d.z,
        m[1] * d.x + m[5] * d.y + m[9] * d.z,
        m[2] * d.x + m[6] * d.y + m[10] * d.z
    );
}

float& Matrix4::at(int row, int col) {
    return m[col * 4 + row];
}

float Matrix4::at(int row, int col) const {
    return m[col * 4 + row];
}

} // namespace ethereal
