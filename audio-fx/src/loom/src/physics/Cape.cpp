#include "Cape.hpp"
#include <cmath>

namespace ethereal {

Cape::Cape() : Cape(Vector2D::zero()) {}

Cape::Cape(const Vector2D& attachPoint, const CapeConfig& config)
    : config(config)
    , attachVelocity(Vector2D::zero()) {
    createParticles(attachPoint);
    createConstraints();
    createBendingConstraints();
}

int Cape::getIndex(int row, int col) const {
    return row * config.width + col;
}

void Cape::createParticles(const Vector2D& attachPoint) {
    particles.clear();
    particles.reserve(config.segments * config.width);

    float halfWidth = (config.width - 1) * config.segmentLength * 0.5f;

    for (int row = 0; row < config.segments; ++row) {
        for (int col = 0; col < config.width; ++col) {
            float x = attachPoint.x + (col * config.segmentLength * 0.6f) - halfWidth * 0.6f;
            float y = attachPoint.y + row * config.segmentLength;
            
            bool isPinned = (row == 0);
            float mass = 1.0f + row * 0.1f;
            
            particles.emplace_back(Vector2D(x, y), mass, isPinned);
            particles.back().damping = config.damping;
        }
    }
}

void Cape::createConstraints() {
    constraints.clear();

    for (int row = 0; row < config.segments; ++row) {
        for (int col = 0; col < config.width; ++col) {
            if (row < config.segments - 1) {
                constraints.emplace_back(
                    &particles[getIndex(row, col)],
                    &particles[getIndex(row + 1, col)],
                    config.segmentLength,
                    config.stiffness
                );
            }

            if (col < config.width - 1) {
                constraints.emplace_back(
                    &particles[getIndex(row, col)],
                    &particles[getIndex(row, col + 1)],
                    config.segmentLength * 0.6f,
                    config.stiffness * 0.8f
                );
            }

            if (row < config.segments - 1 && col < config.width - 1) {
                constraints.emplace_back(
                    &particles[getIndex(row, col)],
                    &particles[getIndex(row + 1, col + 1)],
                    config.stiffness * 0.5f
                );
            }
            if (row < config.segments - 1 && col > 0) {
                constraints.emplace_back(
                    &particles[getIndex(row, col)],
                    &particles[getIndex(row + 1, col - 1)],
                    config.stiffness * 0.5f
                );
            }
        }
    }
}

void Cape::createBendingConstraints() {
    bendConstraints.clear();

    for (int row = 0; row < config.segments - 2; ++row) {
        for (int col = 0; col < config.width; ++col) {
            bendConstraints.emplace_back(
                &particles[getIndex(row, col)],
                &particles[getIndex(row + 1, col)],
                &particles[getIndex(row + 2, col)],
                config.bendStiffness
            );
        }
    }

    for (int row = 0; row < config.segments; ++row) {
        for (int col = 0; col < config.width - 2; ++col) {
            bendConstraints.emplace_back(
                &particles[getIndex(row, col)],
                &particles[getIndex(row, col + 1)],
                &particles[getIndex(row, col + 2)],
                config.bendStiffness * 0.5f
            );
        }
    }
}

void Cape::update(float dt, const WindField& wind) {
    Vector2D gravity(0.0f, config.gravity);

    for (int row = 0; row < config.segments; ++row) {
        for (int col = 0; col < config.width; ++col) {
            auto& particle = particles[getIndex(row, col)];
            
            if (particle.pinned) continue;

            particle.applyForce(gravity * particle.mass);

            Vector2D windForce = wind.getWindAt(particle.position) * config.windInfluence;
            
            float rowFactor = static_cast<float>(row) / config.segments;
            windForce *= (0.5f + rowFactor * 0.5f);
            
            particle.applyForce(windForce);

            Vector2D dragForce = particle.getVelocity() * -0.5f;
            particle.applyForce(dragForce);
        }
    }

    for (auto& particle : particles) {
        particle.update(dt);
    }
}

void Cape::solveConstraints(int iterations) {
    for (int i = 0; i < iterations; ++i) {
        for (auto& constraint : constraints) {
            constraint.solve();
        }
        
        if (i % 2 == 0) {
            for (auto& bend : bendConstraints) {
                bend.solve();
            }
        }
    }
}

void Cape::setAttachPoint(const Vector2D& point) {
    float halfWidth = (config.width - 1) * config.segmentLength * 0.3f;
    
    for (int col = 0; col < config.width; ++col) {
        auto& particle = particles[getIndex(0, col)];
        float x = point.x + (col * config.segmentLength * 0.6f) - halfWidth;
        particle.moveTo(Vector2D(x, point.y));
    }
}

void Cape::setAttachVelocity(const Vector2D& velocity) {
    attachVelocity = velocity;
    
    for (int col = 0; col < config.width; ++col) {
        auto& particle = particles[getIndex(0, col)];
        particle.setVelocity(velocity * 0.1f);
    }
}

VerletParticle& Cape::getParticle(int row, int col) {
    return particles[getIndex(row, col)];
}

const VerletParticle& Cape::getParticle(int row, int col) const {
    return particles[getIndex(row, col)];
}

} // namespace ethereal
