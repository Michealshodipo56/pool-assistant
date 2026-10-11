#include "pool_engine.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <unordered_set>

namespace pool {
namespace {
Vec2 add(Vec2 a, Vec2 b) { return {a.x + b.x, a.y + b.y}; }
Vec2 sub(Vec2 a, Vec2 b) { return {a.x - b.x, a.y - b.y}; }
Vec2 mul(Vec2 a, float s) { return {a.x * s, a.y * s}; }
float dot(Vec2 a, Vec2 b) { return a.x * b.x + a.y * b.y; }
float length(Vec2 a) { return std::sqrt(dot(a, a)); }
Vec2 norm(Vec2 a) { const float l = length(a); return l > 1e-7F ? mul(a, 1.0F / l) : Vec2{}; }
bool moving(const Ball& b, const Config& c) { return !b.pocketed && length(b.v) >= c.stopSpeed; }
SegmentKind kindFor(const Ball& b, bool cueCollided, int firstTarget) {
  if (b.cue) return cueCollided ? SegmentKind::CueDeflection : SegmentKind::CueIncoming;
  return b.id == firstTarget ? SegmentKind::ObjectOutgoing : SegmentKind::Secondary;
}
}

Result simulate(Rect table, const std::vector<Vec2>& pockets, std::vector<Ball> balls,
                bool allLines, const Config& config) {
  Result result;
  if (balls.empty() || config.timeStep <= 0.0F || config.maxSeconds <= 0.0F) return result;
  std::vector<Vec2> segmentStarts;
  segmentStarts.reserve(balls.size());
  for (const auto& b : balls) segmentStarts.push_back(b.p);
  bool cueCollided = false;
  int firstTarget = -1;
  int sampleCounter = 0;
  float time = 0.0F;
  const int maxSteps = std::min(50000, static_cast<int>(std::ceil(config.maxSeconds / config.timeStep)));

  auto emit = [&](std::size_t i, float confidence = 1.0F) {
    Ball& b = balls[i];
    if (length(sub(b.p, segmentStarts[i])) < b.radius * 0.05F) return;
    if (allLines || b.cue || b.id == firstTarget) {
      result.segments.push_back({b.id, segmentStarts[i], b.p, kindFor(b, cueCollided, firstTarget), confidence});
    }
    segmentStarts[i] = b.p;
  };

  for (int step = 0; step < maxSteps; ++step) {
    bool anyMoving = false;
    for (std::size_t i = 0; i < balls.size(); ++i) {
      Ball& b = balls[i];
      if (!moving(b, config)) continue;
      anyMoving = true;
      b.p = add(b.p, mul(b.v, config.timeStep));
      const float speed = length(b.v);
      const float next = std::max(0.0F, speed - config.rollingDeceleration * config.timeStep);
      b.v = mul(norm(b.v), next);

      for (const Vec2 pocket : pockets) {
        if (length(sub(b.p, pocket)) <= b.radius * 1.75F) {
          emit(i);
          b.pocketed = true; b.v = {};
          result.events.push_back({time, EventKind::Pocket, b.id, -1, b.p});
          break;
        }
      }
      if (b.pocketed) continue;
      bool bounced = false;
      if (b.p.x - b.radius < table.left) { b.p.x = table.left + b.radius; b.v.x = std::abs(b.v.x) * config.cushionRestitution; bounced = true; }
      else if (b.p.x + b.radius > table.right) { b.p.x = table.right - b.radius; b.v.x = -std::abs(b.v.x) * config.cushionRestitution; bounced = true; }
      if (b.p.y - b.radius < table.top) { b.p.y = table.top + b.radius; b.v.y = std::abs(b.v.y) * config.cushionRestitution; bounced = true; }
      else if (b.p.y + b.radius > table.bottom) { b.p.y = table.bottom - b.radius; b.v.y = -std::abs(b.v.y) * config.cushionRestitution; bounced = true; }
      if (bounced) { emit(i); result.events.push_back({time, EventKind::Cushion, b.id, -1, b.p}); }
    }

    for (std::size_t i = 0; i < balls.size(); ++i) for (std::size_t j = i + 1; j < balls.size(); ++j) {
      Ball& a = balls[i]; Ball& b = balls[j];
      if (a.pocketed || b.pocketed) continue;
      Vec2 delta = sub(b.p, a.p); const float distance = length(delta); const float minDistance = a.radius + b.radius;
      if (distance >= minDistance || distance < 1e-7F) continue;
      Vec2 n = mul(delta, 1.0F / distance);
      const float relative = dot(sub(b.v, a.v), n);
      const float overlap = minDistance - distance;
      a.p = sub(a.p, mul(n, overlap * 0.5F)); b.p = add(b.p, mul(n, overlap * 0.5F));
      if (relative >= 0.0F) continue;
      emit(i); emit(j);
      if (!cueCollided && (a.cue || b.cue)) {
        cueCollided = true;
        firstTarget = a.cue ? b.id : a.id;
      } else if (!allLines && firstTarget >= 0 && (a.id == firstTarget || b.id == firstTarget)) {
        a.v = {}; b.v = {};
        result.events.push_back({time, EventKind::BallCollision, a.id, b.id, mul(add(a.p, b.p), .5F)});
        continue;
      }
      const float impulse = -(1.0F + config.ballRestitution) * relative * 0.5F;
      a.v = sub(a.v, mul(n, impulse)); b.v = add(b.v, mul(n, impulse));
      result.events.push_back({time, EventKind::BallCollision, a.id, b.id, mul(add(a.p, b.p), .5F)});
    }

    if (static_cast<int>(result.events.size()) >= config.maxEvents) {
      result.events.push_back({time, EventKind::LimitReached, -1, -1, {}});
      for (std::size_t i = 0; i < balls.size(); ++i) emit(i, 0.45F);
      result.balls = std::move(balls); result.complete = false; return result;
    }
    if (++sampleCounter >= 8) { for (std::size_t i = 0; i < balls.size(); ++i) if (moving(balls[i], config)) emit(i); sampleCounter = 0; }
    time += config.timeStep;
    if (!anyMoving) {
      for (std::size_t i = 0; i < balls.size(); ++i) emit(i);
      result.balls = std::move(balls); result.complete = true; return result;
    }
  }
  for (std::size_t i = 0; i < balls.size(); ++i) emit(i, 0.45F);
  result.events.push_back({time, EventKind::LimitReached, -1, -1, {}});
  result.balls = std::move(balls); result.complete = false; return result;
}
}

