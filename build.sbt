name := "traffic_simulator"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.13",
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.13" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.scalafx" %% "scalafx" % "8.0.144-R12",
  "org.scalaz" %% "scalaz-core" % "7.2.24"
)
