#pragma once
#include "Character3D.hpp"
#include "physics/WindField3D.hpp"

namespace ethereal {

struct FlightConfig3D {
    float liftForce = 120.0f;
    float diveForce = 80.0f;
    float horizontalForce = 100.0f;
    float glideRatio = 3.0f;
    float minGlideSpeed = 20.0f;
    float maxGlideSpeed = 200.0f;
    float altitudeGain = 0.8f;
    float speedLossOnClimb = 0.97f;
    float speedGainOnDive = 1.01f;
    float windAssist = 1.0f;
    float turbulenceEffect = 0.4f;
    float bankAngle = 0.4f;
    
    // Mouse control settings
    float mouseSensitivity = 0.003f;
    float turnSmoothing = 8.0f;
    float climbSensitivity = 0.8f;
    float thrustAcceleration = 80.0f;
    float thrustMaxSpeed = 150.0f;
    float idleDeceleration = 0.985f;
    float naturalGlideSpeed = 40.0f;
};

enum class FlightState3D {
    Gliding,
    Climbing,
    Diving,
    Hovering,
    Soaring
};

class FlightController3D {
public:
    FlightController3D();
    FlightController3D(Character3D* character, const FlightConfig3D& config = FlightConfig3D{});

    void update(float dt, const WindField3D& wind);
    
    void moveUp();
    void moveDown();
    void moveLeft();
    void moveRight();
    void moveForward();
    void moveBackward();
    void stopVertical();
    void stopHorizontal();
    void boost();
    
    // Mouse-based controls
    void updateMouseControl(float mouseDeltaX, float mouseDeltaY, bool isFlying, float dt);
    void setFlying(bool flying);
    bool isFlying() const { return flying; }
    
    FlightState3D getState() const { return state; }
    float getEnergy() const { return energy; }
    float getGlideEfficiency() const;
    float getAltitude() const;
    
    void setCharacter(Character3D* character);
    const FlightConfig3D& getConfig() const { return config; }

private:
    Character3D* character;
    FlightConfig3D config;
    FlightState3D state;
    
    bool inputUp, inputDown, inputLeft, inputRight, inputForward, inputBackward;
    bool isBoosting;
    bool flying;
    
    float energy;
    float stateTimer;
    float boostTimer;
    
    // Mouse control state
    float targetYaw;
    float targetPitch;
    float currentYaw;
    float currentPitch;

    void updateState();
    void applyGlidePhysics(float dt);
    void applyWindEffect(float dt, const WindField3D& wind);
};

} // namespace ethereal
