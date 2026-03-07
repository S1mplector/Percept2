#pragma once
#include <vector>
#include "VerletParticle3D.hpp"
#include "WindField3D.hpp"

namespace ethereal {

struct CapeConfig3D {
    int segments = 14;
    int width = 10;
    float segmentLength = 6.0f;
    float widthSpacing = 4.0f;
    float stiffness = 0.92f;
    float bendStiffness = 0.25f;
    float gravity = 25.0f;
    float windInfluence = 1.4f;
    float damping = 0.985f;
    float aerodynamicDrag = 0.02f;
    float liftCoefficient = 0.3f;
};

class Cape3D {
public:
    Cape3D();
    Cape3D(const Vector3D& attachPoint, const Vector3D& forward, const CapeConfig3D& config = CapeConfig3D{});

    void update(float dt, const WindField3D& wind);
    void solveConstraints(int iterations = 5);
    
    void setAttachPoint(const Vector3D& point, const Vector3D& forward);
    void setAttachVelocity(const Vector3D& velocity);
    
    const std::vector<VerletParticle3D>& getParticles() const { return particles; }
    const CapeConfig3D& getConfig() const { return config; }
    
    int getWidth() const { return config.width; }
    int getSegments() const { return config.segments; }
    
    VerletParticle3D& getParticle(int row, int col);
    const VerletParticle3D& getParticle(int row, int col) const;
    
    Vector3D getNormal(int row, int col) const;
    Vector3D getAverageNormal() const;

private:
    std::vector<VerletParticle3D> particles;
    std::vector<VerletConstraint3D> constraints;
    std::vector<BendingConstraint3D> bendConstraints;
    CapeConfig3D config;
    Vector3D attachVelocity;
    Vector3D currentForward;

    void createParticles(const Vector3D& attachPoint, const Vector3D& forward);
    void createConstraints();
    void createBendingConstraints();
    void applyAerodynamics(float dt, const WindField3D& wind);
    
    int getIndex(int row, int col) const;
};

} // namespace ethereal
