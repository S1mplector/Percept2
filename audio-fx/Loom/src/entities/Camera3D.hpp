#pragma once
#include "core/Vector3D.hpp"
#include "core/Quaternion.hpp"
#include "core/Matrix4.hpp"

namespace ethereal {

struct FlightCameraConfig {
    float followDistance = 80.0f;
    float followHeight = 30.0f;
    float smoothSpeed = 3.0f;
    float rotationSmooth = 5.0f;
    float fov = 60.0f;
    float nearPlane = 0.1f;
    float farPlane = 2000.0f;
    float minPitch = -0.5f;
    float maxPitch = 1.2f;
    float orbitSpeed = 2.0f;
};

class FlightCamera {
public:
    FlightCamera();
    FlightCamera(const Vector3D& position, const Vector3D& target, const FlightCameraConfig& config = FlightCameraConfig{});

    void update(float dt);
    void followTarget(const Vector3D& targetPos, const Vector3D& targetVelocity, float dt);
    
    void orbit(float deltaYaw, float deltaPitch);
    void zoom(float delta);
    void setTarget(const Vector3D& target);
    void setPosition(const Vector3D& pos);
    
    const Vector3D& getPosition() const { return position; }
    const Vector3D& getTarget() const { return target; }
    Vector3D getForward() const;
    Vector3D getRight() const;
    Vector3D getUp() const;
    
    Matrix4 getViewMatrix() const;
    Matrix4 getProjectionMatrix(float aspectRatio) const;
    
    float getYaw() const { return yaw; }
    float getPitch() const { return pitch; }
    
    void shake(float intensity, float duration);
    void setConfig(const FlightCameraConfig& cfg) { config = cfg; }
    const FlightCameraConfig& getConfig() const { return config; }

private:
    Vector3D position;
    Vector3D target;
    Vector3D velocity;
    FlightCameraConfig config;
    
    float yaw;
    float pitch;
    float currentDistance;
    
    float shakeIntensity;
    float shakeDuration;
    float shakeTimer;
    
    Vector3D calculateIdealPosition(const Vector3D& targetPos, const Vector3D& targetVelocity) const;
    Vector3D applyShake() const;
};

} // namespace ethereal
