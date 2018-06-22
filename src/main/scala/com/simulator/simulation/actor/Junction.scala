package com.simulator.simulation.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import com.simulator.common.JunctionId
import com.simulator.simulation.actor.Junction.{InDirection, OutDirection}

object Junction {

  def props(junctionId: JunctionId): Props = Props(new Junction(junctionId))

  case class GetRoadLight(road: RoadRef)
  case class RoadLight(road: RoadRef, greenLight: Boolean)

  case object GetState
  final case class State(junctionId: JunctionId,
                         inRoads: List[RoadRef],
                         outRoads: List[RoadRef],
                         roadWithGreenLight: Option[RoadRef],
                         timeToChange: Double)

  sealed trait Direction
  final object OutDirection extends Direction
  final object InDirection extends Direction

  final case class AddRoad(id: ActorRef, direction: Direction)
}

class Junction(val junctionId: JunctionId,
               val greenLightInterval: Int = 5) extends Actor {

  val log = Logging(context.system, this)

  var inRoads: List[ActorRef] = List.empty
  var outRoads: List[ActorRef] = List.empty

  var timeToChange: Int = greenLightInterval
  var greenLightRoad: Option[ActorRef] = None
  var greenLightRoadQueue: List[ActorRef] = List.empty

  override def preStart() {
    log.info("Started")
  }

  override def receive = {
    case Junction.GetState =>
      sender ! Junction.State(junctionId, inRoads, outRoads, greenLightRoad, timeToChange)

    case Junction.GetRoadLight(road) =>
      sender ! Junction.RoadLight(road, greenLightRoad.contains(road))

    case TimeSynchronizer.ComputeTimeSlot =>
      log.info("Computing time slot")

      if (timeToChange == 0) {
        timeToChange = greenLightInterval

        if (greenLightRoadQueue.isEmpty) {
          greenLightRoadQueue = inRoads
        }

        greenLightRoad = Some(greenLightRoadQueue.head)
        greenLightRoadQueue = greenLightRoadQueue.tail

        log.info(s"${ greenLightRoad.get.path } has green light")
      }

      timeToChange -= 1
      sender ! TimeSynchronizer.TimeSlotComputed

    case Junction.AddRoad(road, direction) =>
      direction match {
        case InDirection =>
          inRoads ::= road
          log.info(s"Added in road ${ road.path }")
        case OutDirection =>
          outRoads ::= road
          log.info(s"Added out road ${ road.path }")
      }
  }
}
