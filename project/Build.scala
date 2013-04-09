import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "flightsight"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    "org.reactivemongo" %% "reactivemongo" % "0.8",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.8",
    "com.typesafe.slick" %% "slick" % "1.0.0",
    "org.webjars" % "webjars-play" % "2.1.0-1",
    "org.webjars" % "bootstrap" % "2.3.1",
    "org.webjars" % "jquery" % "1.9.1"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    routesImport ++= Seq(
      "org.joda.time.{DateTime => JodaDateTime}",
      "models._",
      "DateTime._"
    ),
    templatesImport ++= Seq(
      "org.joda.time.{DateTime => JodaDateTime}"
    )
  )

}
