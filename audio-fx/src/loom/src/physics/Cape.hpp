#pragma once
#include <vector>
#include "VerletParticle.hpp"
#include "VerletConstraint.hpp"
#include "WindField.hpp"

namespace ethereal {

struct CapeConfig {
    int segments = 12;
    int width = 8;
    float segmentLength = 8.0f;
    float stiffness = 0.95f;
    float bendStiffness = 0.3f;
    float gravity = 400.0f;
    float windInfluence = 1.2f;
    float damping = 0.98f;
};

class Cape {
public:
    Cape();
    Cape(const Vector2D& attachPoint, const CapeConfig& config = CapeConfig{});

    void update(float dt, const WindField& wind);
    void solveConstraints(int iterations = 4);
    
    void setAttachPoint(const Vector2D& point);
    void setAttachVelocity(const Vector2D& velocity);
    
    const std::vector<VerletParticle>& getParticles() const { return particles; }
    const std::vector<VerletConstraint>& getConstraints() const { return constraints; }
    const CapeConfig& getConfig() const { return config; }
    
    int getWidth() const { return config.width; }
    int getSegments() const { return config.segments; }
    
    VerletParticle& getParticle(int row, int col);
    const VerletParticle& getParticle(int row, int col) const;

private:
    std::vector<VerletParticle> particles;
    std::vector<VerletConstraint> constraints;
    std::vector<BendingConstraint> bendConstraints;
    CapeConfig config;
    Vector2D attachVelocity;

    void createParticles(const Vector2D& attachPoint);
    void createConstraints();
    void createBendingConstraints();
    
    int getIndex(int row, int col) const;
};

} // namespace ethereal
