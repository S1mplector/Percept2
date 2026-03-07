#include "FlightController.hpp"
#include <cmath>
#include <algorithm>

namespace ethereal {

FlightController::FlightController()
    : character(nullptr)
    , config{}
    , state(FlightState::Gliding)
    , inputUp(false)
    , inputDown(false)
    , inputLeft(false)
    , inputRight(false)
    , energy(100.0f)
    , stateTimer(0.0f) {}

FlightController::FlightController(Character* character, const FlightConfig& config)
    : character(character)
    , config(config)
    , state(FlightState::Gliding)
    , inputUp(false)
    , inputDown(false)
    , inputLeft(false)
    , inputRight(false)
    , energy(100.0f)
    , stateTimer(0.0f) {}

void FlightController::update(float dt, const WindField& wind) {
    if (!character) return;

    updateState();
    applyGlidePhysics(dt);
    applyWindEffect(dt, wind);

    Vector2D force = Vector2D::zero();

    if (inputUp) {
        force.y -= config.liftForce;
        energy -= dt * 20.0f;
    }
    if (inputDown) {
        force.y += config.diveForce;
        energy += dt * 5.0f;
    }
    if (inputLeft) {
        force.x -= config.horizontalForce;
    }
    if (inputRight) {
        force.x += config.horizontalForce;
    }

    energy = std::clamp(energy, 0.0f, 100.0f);
    character->applyForce(force);
    
    stateTimer += dt;
}

void FlightController::updateState() {
    if (!character) return;

    const Vector2D& vel = character->getVelocity();
    
    if (inputUp && energy > 0) {
        state = FlightState::Climbing;
    } else if (inputDown) {
        state = FlightState::Diving;
    } else if (std::abs(vel.x) > config.minGlideSpeed || std::abs(vel.y) > 10.0f) {
        state = FlightState::Gliding;
    } else {
        state = FlightState::Hovering;
    }
}

void FlightController::applyGlidePhysics(float dt) {
    if (!character) return;

    Vector2D vel = character->getVelocity();
    float speed = vel.length();

    switch (state) {
        case FlightState::Climbing: {
            if (speed > config.minGlideSpeed) {
                float speedLoss = 1.0f - (1.0f - config.speedLossOnClimb) * dt * 60.0f;
                character->setVelocity(vel * speedLoss);
            }
            break;
        }
        case FlightState::Diving: {
            if (speed < config.maxGlideSpeed) {
                float speedGain = 1.0f + (config.speedGainOnDive - 1.0f) * dt * 60.0f;
                character->setVelocity(vel * speedGain);
            }
            break;
        }
        case FlightState::Gliding: {
            if (vel.y < 0 && speed > config.minGlideSpeed) {
                float lift = -vel.y * config.altitudeGain * dt;
                character->applyForce(Vector2D(0, -lift * 100.0f));
            }
            
            float gravity = 150.0f * (1.0f - std::min(speed / config.maxGlideSpeed, 1.0f) * 0.5f);
            character->applyForce(Vector2D(0, gravity));
            break;
        }
        case FlightState::Hovering: {
            character->applyForce(Vector2D(0, 200.0f));
            break;
        }
    }
}

void FlightController::applyWindEffect(float dt, const WindField& wind) {
    if (!character) return;

    Vector2D windForce = wind.getWindAt(character->getPosition());
    
    windForce *= config.windAssist;
    
    float turbulence = (wind.getStrengthAt(character->getPosition()) / 100.0f) * config.turbulenceEffect;
    Vector2D turbulenceForce = Vector2D(
        (std::sin(stateTimer * 5.0f) * turbulence),
        (std::cos(stateTimer * 7.0f) * turbulence)
    ) * 50.0f;

    character->applyForce(windForce + turbulenceForce);
}

void FlightController::moveUp() { inputUp = true; }
void FlightController::moveDown() { inputDown = true; }
void FlightController::moveLeft() { inputLeft = true; }
void FlightController::moveRight() { inputRight = true; }
void FlightController::stopVertical() { inputUp = false; inputDown = false; }
void FlightController::stopHorizontal() { inputLeft = false; inputRight = false; }

void FlightController::setCharacter(Character* c) { character = c; }

float FlightController::getGlideEfficiency() const {
    if (!character) return 0.0f;
    
    float speed = character->getSpeed();
    float optimalSpeed = (config.minGlideSpeed + config.maxGlideSpeed) * 0.4f;
    float diff = std::abs(speed - optimalSpeed) / optimalSpeed;
    
    return std::max(0.0f, 1.0f - diff);
}

} // namespace ethereal
