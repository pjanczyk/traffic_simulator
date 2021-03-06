package com.simulator

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, OverflowStrategy}
import akka.{Done, NotUsed}
import com.simulator.common.Snapshot
import com.simulator.roadgeneration.{RoadGenerationService, RoadGenerationServiceImpl}
import com.simulator.simulation.{SimulationService, SimulationServiceImpl}
import com.simulator.visualization.{VisualizationService, VisualizationServiceImpl}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.application.{JFXApp, Platform}
import scalafx.scene.Scene
import scalafx.scene.canvas.Canvas
import scalafx.scene.layout.HBox
import scalafx.scene.paint.Color

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.Try

object App extends JFXApp {

  private val canvas = new Canvas

  stage = new PrimaryStage {
    title = "Traffic Simulator"
    scene = new Scene {
      root = new HBox() {
        children = Seq(canvas)

        canvas.width <== this.width
        canvas.height <== this.height
      }
      fill = Color.Black
    }
  }

  private val visualizationService: VisualizationService = new VisualizationServiceImpl(canvas)

  private val roadGenerationService: RoadGenerationService = new RoadGenerationServiceImpl

  private val initialSnapshot = roadGenerationService.generate(Config.worldSize, Config.junctionCount, Config.carCount)

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val simulationService: SimulationService = new SimulationServiceImpl(initialSnapshot)

  Await.ready(simulationService.initialize(), 2 second)

  private val (clockCancellable: Cancellable, finished: Future[Done]) = {
    val clock = Source.tick(initialDelay = 0 second, interval = Config.clockInterval, NotUsed)

    val simulation = Flow[NotUsed]
      .mapAsync(parallelism = 1) { _ => simulationService.simulateTimeSlot() }

    val visualization = Flow[Snapshot]
      .mapAsync(parallelism = 1) { snapshot =>
        val promise = Promise[Unit]()
        Platform.runLater {
          promise.complete(Try {
            visualizationService.visualize(snapshot)
          })
        }
        promise.future
      }

    clock
      .via(simulation)
      .buffer(size = 1, OverflowStrategy.dropHead)
      .async
      .buffer(size = 1, OverflowStrategy.dropHead)
      .withAttributes(Attributes.inputBuffer(1, 1))
      .via(visualization)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }

  finished.failed.foreach { throwable =>
    println("An error occurred. Aborting...")
    throwable.printStackTrace()
    sys.exit(1)
  }

  override def stopApp(): Unit = {
    println("Stopping simulation...")
    clockCancellable.cancel()
    Await.result(finished, 5 seconds)
    println("Stopped")
    system.terminate()
  }
}
