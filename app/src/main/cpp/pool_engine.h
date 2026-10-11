#pragma once
#include <vector>

namespace pool {
struct Vec2 { float x{}, y{}; };
struct Rect { float left{}, top{}, right{}, bottom{}; };
struct Config {
  float rollingDeceleration{0.24F};
  float cushionRestitution{0.82F};
  float ballRestitution{0.96F};
  float stopSpeed{0.025F};
  float timeStep{1.0F / 180.0F};
  float maxSeconds{8.0F};
  int maxEvents{128};
};
struct Ball { int id{}; Vec2 p{}, v{}; float radius{}; bool cue{}; bool pocketed{}; };
enum class SegmentKind : int { CueIncoming, ObjectOutgoing, CueDeflection, Secondary, Uncertain };
enum class EventKind : int { BallCollision, Cushion, Pocket, Stopped, LimitReached };
struct Segment { int id{}; Vec2 from{}, to{}; SegmentKind kind{}; float confidence{1.0F}; };
struct Event { float time{}; EventKind kind{}; int first{}, second{-1}; Vec2 at{}; };
struct Result { std::vector<Segment> segments; std::vector<Event> events; std::vector<Ball> balls; bool complete{}; };

Result simulate(Rect table, const std::vector<Vec2>& pockets, std::vector<Ball> balls,
                bool allLines, const Config& config);
}

