#pragma once
#include "core/Vector2D.hpp"

namespace ethereal {

class VerletParticle {
public:
    Vector2D position;
    Vector2D previousPosition;
    Vector2D acceleration;
    float mass;
    bool pinned;
    float damping;

    VerletParticle();
    VerletParticle(const Vector2D& pos, float mass = 1.0f, bool pinned = false);

    void applyForce(const Vector2D& force);
    void update(float dt);
    
    Vector2D getVelocity() const;
    void setVelocity(const Vector2D& vel);
    
    void pin();
    void unpin();
    void moveTo(const Vector2D& pos);
};

} // namespace ethereal
