#pragma once
#include "core/Vector3D.hpp"
#include "core/Quaternion.hpp"
#include <vector>

namespace ethereal {

struct CharacterConfig3D {
    float radius = 8.0f;
    float maxSpeed = 150.0f;
    float acceleration = 200.0f;
    float drag = 0.985f;
    float capeOffset = 6.0f;
    float rotationSpeed = 5.0f;
    int trailLength = 20;
    float trailSpacing = 0.05f;
};

struct TrailPoint {
    Vector3D position;
    float alpha;
    float size;
};

class Character3D {
public:
    Character3D();
    Character3D(const Vector3D& position, const CharacterConfig3D& config = CharacterConfig3D{});

    void update(float dt);
    
    void setPosition(const Vector3D& pos);
    void setVelocity(const Vector3D& vel);
    void applyForce(const Vector3D& force);
    void rotateYaw(float angle);
    void setYaw(float angle);
    
    const Vector3D& getPosition() const { return position; }
    const Vector3D& getVelocity() const { return velocity; }
    float getSpeed() const { return velocity.length(); }
    float getRadius() const { return config.radius; }
    
    Vector3D getCapeAttachPoint() const;
    Vector3D getForward() const;
    Vector3D getRight() const;
    Vector3D getUp() const;
    const Quaternion& getRotation() const { return rotation; }
    
    const std::vector<TrailPoint>& getTrail() const { return trail; }
    const CharacterConfig3D& getConfig() const { return config; }

private:
    Vector3D position;
    Vector3D velocity;
    Vector3D acceleration;
    Quaternion rotation;
    Quaternion targetRotation;
    CharacterConfig3D config;
    
    std::vector<TrailPoint> trail;
    float trailTimer;
    
    void updateRotation(float dt);
    void updateTrail(float dt);
};

} // namespace ethereal
