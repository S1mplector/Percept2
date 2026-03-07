#include "VerletConstraint.hpp"
#include <cmath>

namespace ethereal {

VerletConstraint::VerletConstraint()
    : particleA(nullptr)
    , particleB(nullptr)
    , restLength(0.0f)
    , stiffness(1.0f)
    , active(true) {}

VerletConstraint::VerletConstraint(VerletParticle* a, VerletParticle* b, float stiffness)
    : particleA(a)
    , particleB(b)
    , stiffness(stiffness)
    , active(true) {
    restLength = (b->position - a->position).length();
}

VerletConstraint::VerletConstraint(VerletParticle* a, VerletParticle* b, float restLength, float stiffness)
    : particleA(a)
    , particleB(b)
    , restLength(restLength)
    , stiffness(stiffness)
    , active(true) {}

void VerletConstraint::solve() {
    if (!active || !particleA || !particleB) return;

    Vector2D delta = particleB->position - particleA->position;
    float currentLength = delta.length();
    
    if (currentLength < 0.0001f) return;

    float diff = (currentLength - restLength) / currentLength;
    Vector2D correction = delta * (diff * 0.5f * stiffness);

    if (!particleA->pinned && !particleB->pinned) {
        float totalMass = particleA->mass + particleB->mass;
        float ratioA = particleB->mass / totalMass;
        float ratioB = particleA->mass / totalMass;
        
        particleA->position += correction * ratioA;
        particleB->position -= correction * ratioB;
    } else if (!particleA->pinned) {
        particleA->position += correction * 2.0f;
    } else if (!particleB->pinned) {
        particleB->position -= correction * 2.0f;
    }
}

void VerletConstraint::setRestLength(float length) {
    restLength = length;
}

float VerletConstraint::getCurrentLength() const {
    if (!particleA || !particleB) return 0.0f;
    return (particleB->position - particleA->position).length();
}

BendingConstraint::BendingConstraint()
    : particleA(nullptr)
    , particleB(nullptr)
    , particleC(nullptr)
    , restAngle(0.0f)
    , stiffness(0.5f) {}

BendingConstraint::BendingConstraint(VerletParticle* a, VerletParticle* b, VerletParticle* c, float stiffness)
    : particleA(a)
    , particleB(b)
    , particleC(c)
    , stiffness(stiffness) {
    Vector2D ba = a->position - b->position;
    Vector2D bc = c->position - b->position;
    restAngle = std::atan2(ba.cross(bc), ba.dot(bc));
}

void BendingConstraint::solve() {
    if (!particleA || !particleB || !particleC) return;

    Vector2D ba = particleA->position - particleB->position;
    Vector2D bc = particleC->position - particleB->position;
    
    float currentAngle = std::atan2(ba.cross(bc), ba.dot(bc));
    float angleDiff = currentAngle - restAngle;
    
    while (angleDiff > 3.14159f) angleDiff -= 6.28318f;
    while (angleDiff < -3.14159f) angleDiff += 6.28318f;
    
    float correction = angleDiff * stiffness * 0.5f;
    
    if (!particleA->pinned) {
        particleA->position = particleB->position + ba.rotated(-correction);
    }
    if (!particleC->pinned) {
        particleC->position = particleB->position + bc.rotated(correction);
    }
}

} // namespace ethereal
