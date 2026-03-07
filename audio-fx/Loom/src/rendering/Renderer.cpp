#include "Renderer.hpp"
#include <cmath>
#include <algorithm>

namespace ethereal {

Renderer::Renderer() : Renderer(RenderConfig{}) {}

Renderer::Renderer(const RenderConfig& config)
    : config(config)
    , initialized(false) {
    particles.reserve(200);
}

Renderer::~Renderer() {
    if (initialized) {
        shutdown();
    }
}

void Renderer::initialize() {
    SetConfigFlags(FLAG_MSAA_4X_HINT | FLAG_VSYNC_HINT);
    InitWindow(config.screenWidth, config.screenHeight, "Ethereal Flight - Cape Physics Demo");
    SetTargetFPS(60);
    initialized = true;

    for (int i = 0; i < 150; ++i) {
        FloatingParticle p;
        p.position = Vector2D(
            static_cast<float>(GetRandomValue(0, config.screenWidth)),
            static_cast<float>(GetRandomValue(0, config.screenHeight))
        );
        p.velocity = Vector2D::zero();
        p.alpha = GetRandomValue(20, 80) / 255.0f;
        p.size = GetRandomValue(1, 4) * 0.5f;
        p.lifetime = GetRandomValue(0, 1000) / 100.0f;
        particles.push_back(p);
    }
}

void Renderer::shutdown() {
    if (initialized) {
        CloseWindow();
        initialized = false;
    }
}

void Renderer::beginFrame() {
    BeginDrawing();
}

void Renderer::endFrame() {
    EndDrawing();
}

void Renderer::drawBackground(float time) {
    ClearBackground(config.backgroundColor);

    for (int i = 0; i < 3; ++i) {
        float offset = std::sin(time * 0.1f + i * 0.5f) * 20.0f;
        float yPos = config.screenHeight * (0.3f + i * 0.2f) + offset;
        
        Color cloudColor = {
            static_cast<unsigned char>(30 + i * 10),
            static_cast<unsigned char>(40 + i * 15),
            static_cast<unsigned char>(60 + i * 20),
            static_cast<unsigned char>(30 - i * 8)
        };
        
        for (int x = -100; x < config.screenWidth + 100; x += 150) {
            float xOffset = std::sin(time * 0.05f + x * 0.01f) * 30.0f;
            DrawEllipse(x + static_cast<int>(xOffset), static_cast<int>(yPos), 
                       200.0f, 60.0f, cloudColor);
        }
    }
}

void Renderer::drawWindField(const WindField& wind, const Vector2D& cameraOffset) {
    if (!config.showWindField) return;

    int gridSize = config.windGridSize;
    
    for (int x = 0; x < config.screenWidth; x += gridSize) {
        for (int y = 0; y < config.screenHeight; y += gridSize) {
            Vector2D worldPos(x + cameraOffset.x, y + cameraOffset.y);
            Vector2D windVec = wind.getWindAt(worldPos);
            
            float strength = std::min(windVec.length() / 100.0f, 1.0f);
            Vector2D dir = windVec.normalized();
            
            float lineLength = 10.0f + strength * 15.0f;
            Vector2D end = Vector2D(x, y) + dir * lineLength;
            
            Color lineColor = config.windColor;
            lineColor.a = static_cast<unsigned char>(30 + strength * 50);
            
            DrawLineEx(
                {static_cast<float>(x), static_cast<float>(y)},
                {end.x, end.y},
                1.0f + strength,
                lineColor
            );
        }
    }
}

void Renderer::drawCapeSegment(const Vector2D& p1, const Vector2D& p2,
                                const Vector2D& p3, const Vector2D& p4,
                                float depth, const Vector2D& offset) {
    Color color = lerpColor(config.capeColor, config.capeHighlight, depth);
    
    Vector2D q1 = p1 - offset;
    Vector2D q2 = p2 - offset;
    Vector2D q3 = p3 - offset;
    Vector2D q4 = p4 - offset;

    DrawTriangle(
        {q1.x, q1.y}, {q3.x, q3.y}, {q2.x, q2.y},
        color
    );
    DrawTriangle(
        {q2.x, q2.y}, {q3.x, q3.y}, {q4.x, q4.y},
        color
    );
}

void Renderer::drawCape(const Cape& cape, const Vector2D& cameraOffset) {
    int segments = cape.getSegments();
    int width = cape.getWidth();

    for (int row = 0; row < segments - 1; ++row) {
        float depth = static_cast<float>(row) / (segments - 1);
        
        for (int col = 0; col < width - 1; ++col) {
            const auto& p1 = cape.getParticle(row, col).position;
            const auto& p2 = cape.getParticle(row, col + 1).position;
            const auto& p3 = cape.getParticle(row + 1, col).position;
            const auto& p4 = cape.getParticle(row + 1, col + 1).position;
            
            drawCapeSegment(p1, p2, p3, p4, depth, cameraOffset);
        }
    }

    Color edgeColor = {200, 160, 120, 180};
    for (int row = 0; row < segments - 1; ++row) {
        const auto& pLeft1 = cape.getParticle(row, 0).position - cameraOffset;
        const auto& pLeft2 = cape.getParticle(row + 1, 0).position - cameraOffset;
        DrawLineEx({pLeft1.x, pLeft1.y}, {pLeft2.x, pLeft2.y}, 2.0f, edgeColor);
        
        const auto& pRight1 = cape.getParticle(row, width - 1).position - cameraOffset;
        const auto& pRight2 = cape.getParticle(row + 1, width - 1).position - cameraOffset;
        DrawLineEx({pRight1.x, pRight1.y}, {pRight2.x, pRight2.y}, 2.0f, edgeColor);
    }

    for (int col = 0; col < width - 1; ++col) {
        const auto& p1 = cape.getParticle(segments - 1, col).position - cameraOffset;
        const auto& p2 = cape.getParticle(segments - 1, col + 1).position - cameraOffset;
        DrawLineEx({p1.x, p1.y}, {p2.x, p2.y}, 2.0f, edgeColor);
    }
}

void Renderer::drawCharacter(const Character& character, const Vector2D& cameraOffset) {
    Vector2D pos = character.getPosition() - cameraOffset;
    float radius = character.getRadius();
    float angle = character.getFacingAngle();

    Color glowColor = {255, 240, 220, 40};
    for (int i = 3; i > 0; --i) {
        DrawCircle(static_cast<int>(pos.x), static_cast<int>(pos.y), 
                  radius + i * 8, glowColor);
    }

    DrawCircle(static_cast<int>(pos.x), static_cast<int>(pos.y), 
              radius, config.characterColor);

    Color innerColor = {255, 250, 245, 255};
    DrawCircle(static_cast<int>(pos.x), static_cast<int>(pos.y), 
              radius * 0.7f, innerColor);

    Vector2D eyeOffset = Vector2D::fromAngle(angle, radius * 0.3f);
    Vector2D eyePos = pos + eyeOffset;
    DrawCircle(static_cast<int>(eyePos.x), static_cast<int>(eyePos.y), 
              radius * 0.15f, {60, 80, 100, 255});

    float speed = character.getSpeed();
    if (speed > 100.0f) {
        int trailCount = std::min(static_cast<int>(speed / 100.0f), 5);
        Vector2D trailDir = Vector2D::fromAngle(angle + 3.14159f, 1.0f);
        for (int i = 1; i <= trailCount; ++i) {
            Vector2D trailPos = pos + trailDir * (i * 15.0f);
            float alpha = 1.0f - (static_cast<float>(i) / (trailCount + 1));
            Color trailColor = {255, 240, 220, static_cast<unsigned char>(alpha * 100)};
            DrawCircle(static_cast<int>(trailPos.x), static_cast<int>(trailPos.y),
                      radius * (0.5f - i * 0.08f), trailColor);
        }
    }
}

void Renderer::drawUI(const FlightController& flight, const PerformanceMonitor& perf) {
    DrawRectangle(10, 10, 300, 100, {0, 0, 0, 120});
    DrawRectangleLines(10, 10, 300, 100, {100, 100, 100, 150});

    DrawText(perf.getStatsString().c_str(), 20, 20, 14, config.uiColor);

    const char* stateText = "";
    Color stateColor = config.uiColor;
    switch (flight.getState()) {
        case FlightState::Gliding:  stateText = "GLIDING";  stateColor = {150, 200, 255, 255}; break;
        case FlightState::Climbing: stateText = "CLIMBING"; stateColor = {255, 200, 150, 255}; break;
        case FlightState::Diving:   stateText = "DIVING";   stateColor = {200, 255, 200, 255}; break;
        case FlightState::Hovering: stateText = "HOVERING"; stateColor = {200, 200, 200, 255}; break;
    }
    DrawText(stateText, 20, 45, 18, stateColor);

    float energy = flight.getEnergy();
    DrawRectangle(20, 75, 200, 15, {40, 40, 40, 200});
    Color energyColor = energy > 30 ? Color{100, 200, 255, 255} : Color{255, 100, 100, 255};
    DrawRectangle(20, 75, static_cast<int>(energy * 2), 15, energyColor);
    DrawText("ENERGY", 230, 75, 14, config.uiColor);

    int bottomY = config.screenHeight - 40;
    DrawText("WASD/Arrows: Move | Space: Boost | V: Toggle Wind", 20, bottomY, 14, {200, 200, 200, 180});
}

void Renderer::drawParticles(const WindField& wind, float time, const Vector2D& cameraOffset) {
    float dt = GetFrameTime();
    updateParticles(dt, wind);

    for (const auto& p : particles) {
        Vector2D screenPos = p.position - cameraOffset;
        
        if (screenPos.x < -50 || screenPos.x > config.screenWidth + 50 ||
            screenPos.y < -50 || screenPos.y > config.screenHeight + 50) {
            continue;
        }

        Color particleColor = {255, 255, 255, static_cast<unsigned char>(p.alpha * 255)};
        DrawCircle(static_cast<int>(screenPos.x), static_cast<int>(screenPos.y), 
                  p.size, particleColor);
    }
}

void Renderer::updateParticles(float dt, const WindField& wind) {
    for (auto& p : particles) {
        Vector2D windForce = wind.getWindAt(p.position) * 0.01f;
        p.velocity = p.velocity * 0.98f + windForce;
        p.position += p.velocity * dt * 60.0f;
        
        p.lifetime += dt;
        p.alpha = 0.1f + std::sin(p.lifetime * 2.0f) * 0.05f;

        if (p.position.x < -100) p.position.x = config.screenWidth + 100;
        if (p.position.x > config.screenWidth + 100) p.position.x = -100;
        if (p.position.y < -100) p.position.y = config.screenHeight + 100;
        if (p.position.y > config.screenHeight + 100) p.position.y = -100;
    }
}

bool Renderer::shouldClose() const {
    return WindowShouldClose();
}

Vector2D Renderer::getScreenCenter() const {
    return Vector2D(config.screenWidth * 0.5f, config.screenHeight * 0.5f);
}

Color Renderer::lerpColor(Color a, Color b, float t) const {
    return {
        static_cast<unsigned char>(a.r + (b.r - a.r) * t),
        static_cast<unsigned char>(a.g + (b.g - a.g) * t),
        static_cast<unsigned char>(a.b + (b.b - a.b) * t),
        static_cast<unsigned char>(a.a + (b.a - a.a) * t)
    };
}

} // namespace ethereal
