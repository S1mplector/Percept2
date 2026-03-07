#include "VerletParticle3D.hpp"
#include <cmath>
#include <algorithm>

namespace ethereal {

VerletParticle3D::VerletParticle3D() 
    : position(Vector3D::zero())
    , previousPosition(Vector3D::zero())
    , acceleration(Vector3D::zero())
    , mass(1.0f)
    , pinned(false)
    , damping(0.99f)
    , radius(0.5f) {}

VerletParticle3D::VerletParticle3D(const Vector3D& pos, float mass, bool pinned)
    : position(pos)
    , previousPosition(pos)
    , acceleration(Vector3D::zero())
    , mass(mass)
    , pinned(pinned)
    , damping(0.99f)
    , radius(0.5f) {}

void VerletParticle3D::applyForce(const Vector3D& force) {
    if (!pinned) {
        acceleration += force / mass;
    }
}

void VerletParticle3D::update(float dt) {
    if (pinned) {
        acceleration = Vector3D::zero();
        return;
    }

    Vector3D velocity = (position - previousPosition) * damping;
    previousPosition = position;
    position = position + velocity + acceleration * (dt * dt);
    acceleration = Vector3D::zero();
}

Vector3D VerletParticle3D::getVelocity() const {
    return position - previousPosition;
}

void VerletParticle3D::setVelocity(const Vector3D& vel) {
    previousPosition = position - vel;
}

void VerletParticle3D::pin() { pinned = true; }
void VerletParticle3D::unpin() { pinned = false; }

void VerletParticle3D::moveTo(const Vector3D& pos) {
    Vector3D delta = pos - position;
    position = pos;
    if (pinned) {
        previousPosition = pos;
    } else {
        previousPosition += delta;
    }
}

void VerletParticle3D::constrainToSphere(const Vector3D& center, float sphereRadius) {
    Vector3D delta = position - center;
    float dist = delta.length();
    if (dist > sphereRadius) {
        position = center + delta.normalized() * sphereRadius;
    }
}

void VerletParticle3D::constrainToPlane(const Vector3D& point, const Vector3D& normal) {
    Vector3D n = normal.normalized();
    float dist = (position - point).dot(n);
    if (dist < 0) {
        position = position - n * dist;
    }
}

// VerletConstraint3D

VerletConstraint3D::VerletConstraint3D()
    : particleA(nullptr), particleB(nullptr), restLength(0), stiffness(1), active(true) {}

VerletConstraint3D::VerletConstraint3D(VerletParticle3D* a, VerletParticle3D* b, float stiffness)
    : particleA(a), particleB(b), stiffness(stiffness), active(true) {
    restLength = (b->position - a->position).length();
}

VerletConstraint3D::VerletConstraint3D(VerletParticle3D* a, VerletParticle3D* b, float restLength, float stiffness)
    : particleA(a), particleB(b), restLength(restLength), stiffness(stiffness), active(true) {}

void VerletConstraint3D::solve() {
    if (!active || !particleA || !particleB) return;

    Vector3D delta = particleB->position - particleA->position;
    float currentLength = delta.length();
    
    if (currentLength < 0.0001f) return;

    float diff = (currentLength - restLength) / currentLength;
    Vector3D correction = delta * (diff * 0.5f * stiffness);

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

float VerletConstraint3D::getCurrentLength() const {
    if (!particleA || !particleB) return 0;
    return (particleB->position - particleA->position).length();
}

// BendingConstraint3D

BendingConstraint3D::BendingConstraint3D()
    : particleA(nullptr), particleB(nullptr), particleC(nullptr), restAngle(0), stiffness(0.5f) {}

BendingConstraint3D::BendingConstraint3D(VerletParticle3D* a, VerletParticle3D* b, VerletParticle3D* c, float stiffness)
    : particleA(a), particleB(b), particleC(c), stiffness(stiffness) {
    Vector3D ba = a->position - b->position;
    Vector3D bc = c->position - b->position;
    float dot = ba.normalized().dot(bc.normalized());
    restAngle = std::acos(std::clamp(dot, -1.0f, 1.0f));
}

void BendingConstraint3D::solve() {
    if (!particleA || !particleB || !particleC) return;

    Vector3D ba = particleA->position - particleB->position;
    Vector3D bc = particleC->position - particleB->position;
    
    float baLen = ba.length();
    float bcLen = bc.length();
    if (baLen < 0.001f || bcLen < 0.001f) return;
    
    Vector3D baNorm = ba / baLen;
    Vector3D bcNorm = bc / bcLen;
    
    float dot = std::clamp(baNorm.dot(bcNorm), -1.0f, 1.0f);
    float currentAngle = std::acos(dot);
    float angleDiff = currentAngle - restAngle;
    
    if (std::abs(angleDiff) < 0.001f) return;
    
    Vector3D axis = baNorm.cross(bcNorm);
    float axisLen = axis.length();
    if (axisLen < 0.001f) return;
    axis = axis / axisLen;
    
    float correction = angleDiff * stiffness * 0.5f;
    
    if (!particleA->pinned) {
        Vector3D rotA = axis.cross(ba) * correction;
        particleA->position += rotA;
    }
    if (!particleC->pinned) {
        Vector3D rotC = axis.cross(bc) * (-correction);
        particleC->position += rotC;
    }
}

} // namespace ethereal
