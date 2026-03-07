#include "VerletParticle.hpp"

namespace ethereal {

VerletParticle::VerletParticle() 
    : position(Vector2D::zero())
    , previousPosition(Vector2D::zero())
    , acceleration(Vector2D::zero())
    , mass(1.0f)
    , pinned(false)
    , damping(0.99f) {}

VerletParticle::VerletParticle(const Vector2D& pos, float mass, bool pinned)
    : position(pos)
    , previousPosition(pos)
    , acceleration(Vector2D::zero())
    , mass(mass)
    , pinned(pinned)
    , damping(0.99f) {}

void VerletParticle::applyForce(const Vector2D& force) {
    if (!pinned) {
        acceleration += force / mass;
    }
}

void VerletParticle::update(float dt) {
    if (pinned) {
        acceleration = Vector2D::zero();
        return;
    }

    Vector2D velocity = (position - previousPosition) * damping;
    previousPosition = position;
    position = position + velocity + acceleration * (dt * dt);
    acceleration = Vector2D::zero();
}

Vector2D VerletParticle::getVelocity() const {
    return position - previousPosition;
}

void VerletParticle::setVelocity(const Vector2D& vel) {
    previousPosition = position - vel;
}

void VerletParticle::pin() {
    pinned = true;
}

void VerletParticle::unpin() {
    pinned = false;
}

void VerletParticle::moveTo(const Vector2D& pos) {
    Vector2D delta = pos - position;
    position = pos;
    if (pinned) {
        previousPosition = pos;
    } else {
        previousPosition += delta;
    }
}

} // namespace ethereal
