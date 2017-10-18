name := "akka-spans-example"

version := "1.0-SNAPSHOT"

lazy val root = project in file(".")

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.6",
    "io.opentracing" % "opentracing-api" % "0.31.0-RC1",
    "io.opentracing" % "opentracing-util" % "0.31.0-RC1",
    "io.opentracing" % "opentracing-mock" % "0.31.0-RC1"
)

