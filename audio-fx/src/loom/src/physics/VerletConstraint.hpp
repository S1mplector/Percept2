#pragma once
#include "VerletParticle.hpp"

namespace ethereal {

class VerletConstraint {
public:
    VerletParticle* particleA;
    VerletParticle* particleB;
    float restLength;
    float stiffness;
    bool active;

    VerletConstraint();
    VerletConstraint(VerletParticle* a, VerletParticle* b, float stiffness = 1.0f);
    VerletConstraint(VerletParticle* a, VerletParticle* b, float restLength, float stiffness);

    void solve();
    void setRestLength(float length);
    float getCurrentLength() const;
};

class BendingConstraint {
public:
    VerletParticle* particleA;
    VerletParticle* particleB;
    VerletParticle* particleC;
    float restAngle;
    float stiffness;

    BendingConstraint();
    BendingConstraint(VerletParticle* a, VerletParticle* b, VerletParticle* c, float stiffness = 0.5f);

    void solve();
};

} // namespace ethereal
