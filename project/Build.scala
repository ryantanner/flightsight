import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "flightsight"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    "org.reactivemongo" %% "reactivemongo" % "0.9",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.9",
    "org.webjars" % "webjars-play" % "2.1.0-1",
    "org.webjars" % "bootstrap" % "2.3.1",
    "org.webjars" % "jquery" % "1.9.1",
    "org.mindrot" % "jbcrypt" % "0.3m"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    scalaVersion := "2.10.0",
    resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    routesImport ++= Seq(
      "org.joda.time.{DateTime => JodaDateTime}",
      "models.DateTime._"
    ),
    templatesImport ++= Seq(
      "org.joda.time.{DateTime => JodaDateTime}",
      "org.joda.time.format.DateTimeFormat"
    ),
    coffeescriptOptions := Seq("bare"),
    requireJs += "application.js"
  )

}
