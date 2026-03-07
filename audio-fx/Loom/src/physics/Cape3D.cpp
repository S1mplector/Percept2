#include "Cape3D.hpp"
#include <cmath>

namespace ethereal {

Cape3D::Cape3D() : Cape3D(Vector3D::zero(), Vector3D(0, 0, -1)) {}

Cape3D::Cape3D(const Vector3D& attachPoint, const Vector3D& forward, const CapeConfig3D& config)
    : config(config)
    , attachVelocity(Vector3D::zero())
    , currentForward(forward.normalized()) {
    createParticles(attachPoint, forward);
    createConstraints();
    createBendingConstraints();
}

int Cape3D::getIndex(int row, int col) const {
    return row * config.width + col;
}

void Cape3D::createParticles(const Vector3D& attachPoint, const Vector3D& forward) {
    particles.clear();
    particles.reserve(config.segments * config.width);

    Vector3D fwd = forward.normalized();
    Vector3D right = Vector3D(0, 1, 0).cross(fwd).normalized();
    if (right.lengthSquared() < 0.01f) {
        right = Vector3D(1, 0, 0).cross(fwd).normalized();
    }
    
    float halfWidth = (config.width - 1) * config.widthSpacing * 0.5f;

    for (int row = 0; row < config.segments; ++row) {
        for (int col = 0; col < config.width; ++col) {
            Vector3D pos = attachPoint;
            pos += fwd * (-row * config.segmentLength);
            pos += right * (col * config.widthSpacing - halfWidth);
            
            bool isPinned = (row == 0);
            float mass = 1.0f + row * 0.08f;
            
            particles.emplace_back(pos, mass, isPinned);
            particles.back().damping = config.damping;
        }
    }
}

void Cape3D::createConstraints() {
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
                    config.widthSpacing,
                    config.stiffness * 0.9f
                );
            }

            if (row < config.segments - 1 && col < config.width - 1) {
                float diagLen = std::sqrt(config.segmentLength * config.segmentLength + 
                                         config.widthSpacing * config.widthSpacing);
                constraints.emplace_back(
                    &particles[getIndex(row, col)],
                    &particles[getIndex(row + 1, col + 1)],
                    diagLen,
                    config.stiffness * 0.5f
                );
            }
            if (row < config.segments - 1 && col > 0) {
                float diagLen = std::sqrt(config.segmentLength * config.segmentLength + 
                                         config.widthSpacing * config.widthSpacing);
                constraints.emplace_back(
                    &particles[getIndex(row, col)],
                    &particles[getIndex(row + 1, col - 1)],
                    diagLen,
                    config.stiffness * 0.5f
                );
            }
        }
    }
}

void Cape3D::createBendingConstraints() {
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
                config.bendStiffness * 0.6f
            );
        }
    }
}

void Cape3D::applyAerodynamics(float dt, const WindField3D& wind) {
    for (int row = 1; row < config.segments - 1; ++row) {
        for (int col = 1; col < config.width - 1; ++col) {
            auto& particle = particles[getIndex(row, col)];
            if (particle.pinned) continue;

            Vector3D normal = getNormal(row, col);
            Vector3D windVel = wind.getWindAt(particle.position);
            Vector3D relativeVel = windVel - particle.getVelocity();
            
            float normalComponent = relativeVel.dot(normal);
            
            Vector3D dragForce = normal * (normalComponent * config.aerodynamicDrag * std::abs(normalComponent));
            particle.applyForce(dragForce);
            
            if (normalComponent > 0) {
                Vector3D liftDir = Vector3D(0, 1, 0) - normal * normal.y;
                if (liftDir.lengthSquared() > 0.01f) {
                    liftDir = liftDir.normalized();
                    float liftMagnitude = normalComponent * normalComponent * config.liftCoefficient;
                    particle.applyForce(liftDir * liftMagnitude);
                }
            }
        }
    }
}

void Cape3D::update(float dt, const WindField3D& wind) {
    Vector3D gravity(0.0f, -config.gravity, 0.0f);

    for (int row = 0; row < config.segments; ++row) {
        for (int col = 0; col < config.width; ++col) {
            auto& particle = particles[getIndex(row, col)];
            if (particle.pinned) continue;

            // Gravity with mass
            particle.applyForce(gravity * particle.mass);

            // Wind force increases toward cape end
            Vector3D windForce = wind.getWindAt(particle.position) * config.windInfluence;
            float rowFactor = static_cast<float>(row) / config.segments;
            windForce *= (0.3f + rowFactor * 0.7f);
            particle.applyForce(windForce);

            // Movement-based billowing - cape flows behind when moving
            Vector3D moveForce = attachVelocity * (-0.08f * rowFactor);
            particle.applyForce(moveForce);
            
            // Subtle lateral sway based on movement
            float sway = std::sin(row * 0.5f + dt * 3.0f) * attachVelocity.length() * 0.002f;
            Vector3D swayForce = currentForward.cross(Vector3D(0, 1, 0)) * sway;
            particle.applyForce(swayForce);

            // Air resistance / drag
            Vector3D vel = particle.getVelocity();
            float speed = vel.length();
            if (speed > 0.1f) {
                Vector3D dragForce = vel.normalized() * (-0.15f * speed * speed * 0.01f);
                particle.applyForce(dragForce);
            }
        }
    }

    applyAerodynamics(dt, wind);

    for (auto& particle : particles) {
        particle.update(dt);
    }
}

void Cape3D::solveConstraints(int iterations) {
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

void Cape3D::setAttachPoint(const Vector3D& point, const Vector3D& forward) {
    currentForward = forward.normalized();
    Vector3D right = Vector3D(0, 1, 0).cross(currentForward).normalized();
    if (right.lengthSquared() < 0.01f) {
        right = Vector3D(1, 0, 0).cross(currentForward).normalized();
    }
    
    float halfWidth = (config.width - 1) * config.widthSpacing * 0.5f;
    
    for (int col = 0; col < config.width; ++col) {
        auto& particle = particles[getIndex(0, col)];
        Vector3D pos = point + right * (col * config.widthSpacing - halfWidth);
        particle.moveTo(pos);
    }
}

void Cape3D::setAttachVelocity(const Vector3D& velocity) {
    attachVelocity = velocity;
    for (int col = 0; col < config.width; ++col) {
        auto& particle = particles[getIndex(0, col)];
        particle.setVelocity(velocity * 0.05f);
    }
}

VerletParticle3D& Cape3D::getParticle(int row, int col) {
    return particles[getIndex(row, col)];
}

const VerletParticle3D& Cape3D::getParticle(int row, int col) const {
    return particles[getIndex(row, col)];
}

Vector3D Cape3D::getNormal(int row, int col) const {
    row = std::clamp(row, 1, config.segments - 2);
    col = std::clamp(col, 1, config.width - 2);
    
    const Vector3D& center = particles[getIndex(row, col)].position;
    const Vector3D& up = particles[getIndex(row - 1, col)].position;
    const Vector3D& down = particles[getIndex(row + 1, col)].position;
    const Vector3D& left = particles[getIndex(row, col - 1)].position;
    const Vector3D& right = particles[getIndex(row, col + 1)].position;
    
    Vector3D tangentV = (down - up).normalized();
    Vector3D tangentH = (right - left).normalized();
    
    return tangentH.cross(tangentV).normalized();
}

Vector3D Cape3D::getAverageNormal() const {
    Vector3D sum = Vector3D::zero();
    int count = 0;
    
    for (int row = 1; row < config.segments - 1; ++row) {
        for (int col = 1; col < config.width - 1; ++col) {
            sum += getNormal(row, col);
            count++;
        }
    }
    
    return count > 0 ? (sum / static_cast<float>(count)).normalized() : Vector3D(0, 0, 1);
}

} // namespace ethereal
