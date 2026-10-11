#include <jni.h>
#include <vector>
#include "pool_engine.h"

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_poolassistant_physics_NativePhysicsBridge_simulateNative(
    JNIEnv* env, jobject, jfloatArray data, jint ballCount, jint pocketCount, jint mode, jint maxEvents) {
  const jsize size = env->GetArrayLength(data);
  std::vector<float> input(static_cast<std::size_t>(size));
  env->GetFloatArrayRegion(data, 0, size, input.data());
  if (size < 10 || ballCount < 0 || pocketCount < 0 || size < 10 + pocketCount * 2 + ballCount * 7) {
    jfloatArray empty = env->NewFloatArray(4); float header[4]{}; env->SetFloatArrayRegion(empty, 0, 4, header); return empty;
  }
  std::size_t i = 0;
  pool::Rect table{input[i++], input[i++], input[i++], input[i++]};
  pool::Config config; config.rollingDeceleration = input[i++]; config.cushionRestitution = input[i++];
  config.ballRestitution = input[i++]; config.stopSpeed = input[i++]; config.timeStep = input[i++]; config.maxSeconds = input[i++]; config.maxEvents = maxEvents;
  std::vector<pool::Vec2> pockets; pockets.reserve(pocketCount);
  for (int p = 0; p < pocketCount; ++p) pockets.push_back({input[i++], input[i++]});
  std::vector<pool::Ball> balls; balls.reserve(ballCount);
  for (int b = 0; b < ballCount; ++b) {
    pool::Ball ball; ball.id = static_cast<int>(input[i++]); ball.p = {input[i++], input[i++]}; ball.v = {input[i++], input[i++]};
    ball.radius = input[i++]; ball.cue = input[i++] > .5F; balls.push_back(ball);
  }
  pool::Result result = pool::simulate(table, pockets, std::move(balls), mode == 1, config);
  std::vector<float> out;
  out.reserve(4 + result.segments.size() * 7 + result.events.size() * 6 + result.balls.size() * 3);
  out.push_back(result.complete ? 1.0F : 0.0F); out.push_back(static_cast<float>(result.segments.size()));
  out.push_back(static_cast<float>(result.events.size())); out.push_back(static_cast<float>(result.balls.size()));
  for (const auto& s : result.segments) {
    out.insert(out.end(), {static_cast<float>(s.id), s.from.x, s.from.y, s.to.x, s.to.y, static_cast<float>(s.kind), s.confidence});
  }
  for (const auto& e : result.events) {
    out.insert(out.end(), {e.time, static_cast<float>(e.kind), static_cast<float>(e.first), static_cast<float>(e.second), e.at.x, e.at.y});
  }
  for (const auto& b : result.balls) out.insert(out.end(), {static_cast<float>(b.id), b.p.x, b.p.y});
  jfloatArray output = env->NewFloatArray(static_cast<jsize>(out.size()));
  env->SetFloatArrayRegion(output, 0, static_cast<jsize>(out.size()), out.data()); return output;
}

