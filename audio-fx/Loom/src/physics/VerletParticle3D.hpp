#pragma once
#include "core/Vector3D.hpp"

namespace ethereal {

class VerletParticle3D {
public:
    Vector3D position;
    Vector3D previousPosition;
    Vector3D acceleration;
    float mass;
    bool pinned;
    float damping;
    float radius;

    VerletParticle3D();
    VerletParticle3D(const Vector3D& pos, float mass = 1.0f, bool pinned = false);

    void applyForce(const Vector3D& force);
    void update(float dt);
    
    Vector3D getVelocity() const;
    void setVelocity(const Vector3D& vel);
    
    void pin();
    void unpin();
    void moveTo(const Vector3D& pos);
    
    void constrainToSphere(const Vector3D& center, float sphereRadius);
    void constrainToPlane(const Vector3D& point, const Vector3D& normal);
};

class VerletConstraint3D {
public:
    VerletParticle3D* particleA;
    VerletParticle3D* particleB;
    float restLength;
    float stiffness;
    bool active;

    VerletConstraint3D();
    VerletConstraint3D(VerletParticle3D* a, VerletParticle3D* b, float stiffness = 1.0f);
    VerletConstraint3D(VerletParticle3D* a, VerletParticle3D* b, float restLength, float stiffness);

    void solve();
    float getCurrentLength() const;
};

class BendingConstraint3D {
public:
    VerletParticle3D* particleA;
    VerletParticle3D* particleB;
    VerletParticle3D* particleC;
    float restAngle;
    float stiffness;

    BendingConstraint3D();
    BendingConstraint3D(VerletParticle3D* a, VerletParticle3D* b, VerletParticle3D* c, float stiffness = 0.5f);

    void solve();
};

} // namespace ethereal
