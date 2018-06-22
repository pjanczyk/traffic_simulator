package com.simulator.simulation.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.simulator.common.CarId
import com.simulator.simulation.actor.Road.EnterRoad
import scalaz.StreamT.Done

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Car {
  def props(carId: CarId, road: RoadRef, positionOnRoad: Double): Props =
    Props(new Car(carId, road, positionOnRoad))

  case object GetState
  final case class GetStateResult(carId: CarId,
                                  road: RoadRef,
                                  positionOnRoad: Double,
                                  velocity: Double)

  case class EnteredRoad(position: Double, roadLength: Double, carAhead: Option[CarRef], endJunction: JunctionRef)

  case object GetRoadPosition
  case class RoadPosition(road: RoadRef, position: Double)
}

class Car(carId: CarId, initialRoad: RoadRef, initialPosition: Double) extends Actor {

  implicit val ec: ExecutionContext = context.dispatcher
  val log = Logging(context.system, this)
  val random = new Random

  var road: ActorRef = _
  var roadLength: Double = _
  var position: Double = _
  var carAhead: Option[CarRef] = _
  var nextJunction: JunctionRef = _

  override def preStart() {
    log.info("Started")
    initialRoad ! EnterRoad(initialPosition)
  }

  def receive = {
    case Car.GetState =>
      sender ! Car.GetStateResult(carId, road, position, position)

    case Car.EnteredRoad(position, roadLength, carAhead, nextJunction) =>
      this.road = sender
      this.roadLength = roadLength
      this.position = position
      this.carAhead = carAhead
      this.nextJunction = nextJunction

    case Car.GetRoadPosition =>
      sender ! Car.RoadPosition(road, position)

    case TimeSynchronizer.ComputeTimeSlot =>
      log.info("Computing time slot")
      implicit val timeout: Timeout = 1 second

      val timeSynchronizer = sender

      val futureJunctionState = (nextJunction ? Junction.GetState).mapTo[Junction.State]

      val futureMaybeCarAheadPosition = carAhead
        .map { car =>
          (car ? Car.GetRoadPosition).mapTo[Car.RoadPosition]
            .map { Some(_) }
        }
        .getOrElse(Future.successful(None))
        .map {
          _.collect {
            case Car.RoadPosition(road, position) if road == this.road && position <= roadLength =>
              position
          }
        }

      val futureNextPositionComputed =
        for {
          junctionState <- futureJunctionState
          maybeCarAheadPosition <- futureMaybeCarAheadPosition
        } yield {

          val hasGreenLight = junctionState.roadWithGreenLight.contains(road)
          val junctionOutRoads = junctionState.outRoads

          val increasedPosition = position + 0.5

          maybeCarAheadPosition match {
            case Some(carAheadPosition) =>
              val allowedPosition = carAheadPosition - 0.5
              position = math.min(increasedPosition, allowedPosition)

            case None =>
              if (increasedPosition >= roadLength && hasGreenLight) {
                val nextRoad = junctionOutRoads(random.nextInt(junctionOutRoads.size))

                position = roadLength
                road ! Road.LeaveRoad
                nextRoad ! Road.EnterRoad(position = 0.0)

              } else {
                position = math.min(increasedPosition, roadLength)
              }
          }

          Done
        }

      futureNextPositionComputed.map { _ => TimeSynchronizer.TimeSlotComputed }
        .pipeTo(timeSynchronizer)
  }
}
