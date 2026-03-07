#include "Character3D.hpp"
#include <cmath>
#include <algorithm>

namespace ethereal {

Character3D::Character3D() : Character3D(Vector3D::zero()) {}

Character3D::Character3D(const Vector3D& position, const CharacterConfig3D& config)
    : position(position)
    , velocity(Vector3D::zero())
    , acceleration(Vector3D::zero())
    , rotation(Quaternion::identity())
    , targetRotation(Quaternion::identity())
    , config(config)
    , trailTimer(0) {
    trail.reserve(config.trailLength);
}

void Character3D::update(float dt) {
    velocity += acceleration * dt;
    
    float speed = velocity.length();
    if (speed > config.maxSpeed) {
        velocity = velocity.normalized() * config.maxSpeed;
    }
    
    velocity *= config.drag;
    position += velocity * dt;
    
    updateRotation(dt);
    updateTrail(dt);
    
    acceleration = Vector3D::zero();
}

void Character3D::updateRotation(float dt) {
    // Smoothly interpolate to target rotation (set by rotateYaw or externally)
    // Only apply slight pitch based on vertical velocity for visual tilt
    float speed = velocity.length();
    
    if (speed > 20.0f) {
        // Add subtle pitch tilt based on climb/dive
        float pitch = std::atan2(-velocity.y, std::sqrt(velocity.x * velocity.x + velocity.z * velocity.z));
        pitch = std::clamp(pitch * 0.3f, -0.4f, 0.4f); // Limit tilt angle
        
        // Get current yaw from target rotation
        Vector3D fwd = targetRotation * Vector3D(0, 0, 1);
        float yaw = std::atan2(fwd.x, fwd.z);
        
        // Reconstruct target with yaw preserved and pitch added
        Quaternion yawQ = Quaternion::fromAxisAngle(Vector3D(0, 1, 0), yaw);
        Quaternion pitchQ = Quaternion::fromAxisAngle(Vector3D(1, 0, 0), pitch);
        targetRotation = yawQ * pitchQ;
    }
    
    rotation = Quaternion::slerp(rotation, targetRotation, config.rotationSpeed * dt);
}

void Character3D::updateTrail(float dt) {
    trailTimer += dt;
    
    float speed = velocity.length();
    float interval = config.trailSpacing / std::max(speed * 0.01f, 0.5f);
    
    if (trailTimer >= interval && speed > 10.0f) {
        trailTimer = 0;
        
        TrailPoint point;
        point.position = position - velocity.normalized() * config.radius;
        point.alpha = 1.0f;
        point.size = config.radius * 0.8f * std::min(speed / config.maxSpeed, 1.0f);
        
        trail.insert(trail.begin(), point);
        
        while (trail.size() > static_cast<size_t>(config.trailLength)) {
            trail.pop_back();
        }
    }
    
    for (size_t i = 0; i < trail.size(); ++i) {
        float t = static_cast<float>(i) / config.trailLength;
        trail[i].alpha = (1.0f - t) * 0.6f;
        trail[i].size *= 0.98f;
    }
    
    trail.erase(std::remove_if(trail.begin(), trail.end(),
        [](const TrailPoint& p) { return p.alpha < 0.01f || p.size < 0.1f; }), trail.end());
}

void Character3D::setPosition(const Vector3D& pos) {
    position = pos;
}

void Character3D::setVelocity(const Vector3D& vel) {
    velocity = vel;
}

void Character3D::applyForce(const Vector3D& force) {
    acceleration += force;
}

void Character3D::rotateYaw(float angle) {
    Quaternion yawRotation = Quaternion::fromAxisAngle(Vector3D(0, 1, 0), angle);
    targetRotation = yawRotation * targetRotation;
    rotation = yawRotation * rotation;
}

void Character3D::setYaw(float angle) {
    // Set absolute yaw angle
    targetRotation = Quaternion::fromAxisAngle(Vector3D(0, 1, 0), angle);
}

Vector3D Character3D::getCapeAttachPoint() const {
    Vector3D back = rotation * Vector3D(0, 0, -1);
    return position + back * config.capeOffset + Vector3D(0, config.radius * 0.3f, 0);
}

Vector3D Character3D::getForward() const {
    return rotation * Vector3D(0, 0, 1);
}

Vector3D Character3D::getRight() const {
    return rotation * Vector3D(1, 0, 0);
}

Vector3D Character3D::getUp() const {
    return rotation * Vector3D(0, 1, 0);
}

} // namespace ethereal
