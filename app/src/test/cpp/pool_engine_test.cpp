#include "../../main/cpp/pool_engine.h"
#include <cassert>
#include <cmath>
#include <iostream>
using namespace pool;

void directCollision() {
  Config c; c.rollingDeceleration = 0.02F; c.maxSeconds = .65F;
  std::vector<Ball> balls{{0,{2,2},{2,0},.1F,true,false},{1,{3,2},{0,0},.1F,false,false}};
  auto r = simulate({0,0,10,4}, {}, balls, true, c);
  assert(!r.events.empty()); assert(r.balls[1].p.x > 3.0F); assert(r.balls[0].v.x < r.balls[1].v.x);
}
void cushion() {
  Config c; c.rollingDeceleration = 0; c.cushionRestitution = .8F; c.maxSeconds = .5F;
  auto r = simulate({0,0,2,2}, {}, {{0,{1,1},{4,0},.1F,true,false}}, true, c);
  bool hit=false; for(auto e:r.events) hit |= e.kind==EventKind::Cushion; assert(hit); assert(r.balls[0].v.x < 0);
}
void chain() {
  Config c; c.rollingDeceleration=.01F; c.maxSeconds=2;
  auto r=simulate({0,0,6,3},{},{{0,{1,1.5F},{3,0},.1F,true,false},{1,{2,1.5F},{0,0},.1F,false,false},{2,{3,1.5F},{0,0},.1F,false,false}},true,c);
  int collisions=0; for(auto e:r.events) collisions += e.kind==EventKind::BallCollision; assert(collisions>=2); assert(r.balls[2].p.x>3);
}
void pocket() {
  Config c; c.rollingDeceleration=0; c.maxSeconds=1;
  auto r=simulate({0,0,4,2},{{3,1}},{{0,{1,1},{3,0},.1F,true,false}},true,c);
  bool found=false; for(auto e:r.events) found |= e.kind==EventKind::Pocket; assert(found); assert(r.balls[0].pocketed);
}
int main(){ directCollision(); cushion(); chain(); pocket(); std::cout << "pool_engine_test: PASS\n"; }

