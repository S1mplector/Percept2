#pragma once
#include "core/Vector2D.hpp"
#include "physics/Cape.hpp"

namespace ethereal {

struct CharacterConfig {
    float radius = 20.0f;
    float maxSpeed = 600.0f;
    float acceleration = 800.0f;
    float drag = 0.98f;
    float capeOffset = 15.0f;
};

class Character {
public:
    Character();
    Character(const Vector2D& position, const CharacterConfig& config = CharacterConfig{});

    void update(float dt);
    
    void setPosition(const Vector2D& pos);
    void setVelocity(const Vector2D& vel);
    void applyForce(const Vector2D& force);
    
    const Vector2D& getPosition() const { return position; }
    const Vector2D& getVelocity() const { return velocity; }
    float getSpeed() const { return velocity.length(); }
    float getRadius() const { return config.radius; }
    
    Vector2D getCapeAttachPoint() const;
    float getFacingAngle() const { return facingAngle; }
    
    const CharacterConfig& getConfig() const { return config; }

private:
    Vector2D position;
    Vector2D velocity;
    Vector2D acceleration;
    CharacterConfig config;
    float facingAngle;
};

} // namespace ethereal
