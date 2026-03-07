#include "Character.hpp"
#include <cmath>

namespace ethereal {

Character::Character() : Character(Vector2D::zero()) {}

Character::Character(const Vector2D& position, const CharacterConfig& config)
    : position(position)
    , velocity(Vector2D::zero())
    , acceleration(Vector2D::zero())
    , config(config)
    , facingAngle(0.0f) {}

void Character::update(float dt) {
    velocity += acceleration * dt;
    
    float speed = velocity.length();
    if (speed > config.maxSpeed) {
        velocity = velocity.normalized() * config.maxSpeed;
    }
    
    velocity *= config.drag;
    
    position += velocity * dt;
    
    if (speed > 1.0f) {
        float targetAngle = std::atan2(velocity.y, velocity.x);
        float angleDiff = targetAngle - facingAngle;
        
        while (angleDiff > 3.14159f) angleDiff -= 6.28318f;
        while (angleDiff < -3.14159f) angleDiff += 6.28318f;
        
        facingAngle += angleDiff * 0.1f;
    }
    
    acceleration = Vector2D::zero();
}

void Character::setPosition(const Vector2D& pos) {
    position = pos;
}

void Character::setVelocity(const Vector2D& vel) {
    velocity = vel;
}

void Character::applyForce(const Vector2D& force) {
    acceleration += force;
}

Vector2D Character::getCapeAttachPoint() const {
    Vector2D offset = Vector2D::fromAngle(facingAngle + 3.14159f, config.capeOffset);
    return position + offset;
}

} // namespace ethereal
