#pragma once
#include "Character.hpp"
#include "physics/WindField.hpp"

namespace ethereal {

struct FlightConfig {
    float liftForce = 500.0f;
    float diveForce = 300.0f;
    float horizontalForce = 400.0f;
    float glideRatio = 2.5f;
    float minGlideSpeed = 50.0f;
    float maxGlideSpeed = 800.0f;
    float altitudeGain = 0.6f;
    float speedLossOnClimb = 0.95f;
    float speedGainOnDive = 1.02f;
    float windAssist = 0.8f;
    float turbulenceEffect = 0.3f;
};

enum class FlightState {
    Gliding,
    Climbing,
    Diving,
    Hovering
};

class FlightController {
public:
    FlightController();
    FlightController(Character* character, const FlightConfig& config = FlightConfig{});

    void update(float dt, const WindField& wind);
    
    void moveUp();
    void moveDown();
    void moveLeft();
    void moveRight();
    void stopVertical();
    void stopHorizontal();
    
    FlightState getState() const { return state; }
    float getEnergy() const { return energy; }
    float getGlideEfficiency() const;
    
    void setCharacter(Character* character);
    const FlightConfig& getConfig() const { return config; }

private:
    Character* character;
    FlightConfig config;
    FlightState state;
    
    bool inputUp;
    bool inputDown;
    bool inputLeft;
    bool inputRight;
    
    float energy;
    float stateTimer;

    void updateState();
    void applyGlidePhysics(float dt);
    void applyWindEffect(float dt, const WindField& wind);
};

} // namespace ethereal
