package com.simulator.common

import scala.collection.immutable.Seq

case class JunctionState(id: JunctionId,
                         position: Vec2D,
                         greenLightRoad: Option[RoadId] = None)

case class RoadState(id: RoadId,
                     start: JunctionId,
                     end: JunctionId)

case class CarState(id: CarId,
                    road: RoadId,
                    positionOnRoad: Double = 0.0f)

case class Snapshot(junctions: Seq[JunctionState],
                    roads: Seq[RoadState],
                    cars: Seq[CarState])
