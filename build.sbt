scalaVersion := "3.3.1"

name := "radgiver"
organization := "anna.rookwill"
version := "1.0"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.0.19",
  "dev.zio" %% "zio-json" % "0.6.2",
  "dev.zio" %% "zio-http" % "3.0.0-RC2",
  "dev.zio" %% "zio-config" % "4.0.0-RC16",
  "dev.zio" %% "zio-config-typesafe" % "4.0.0-RC16",
  "dev.zio" %% "zio-config-magnolia" % "4.0.0-RC16",
  "com.softwaremill.quicklens" %% "quicklens" % "1.9.6",
  "com.github.jwt-scala" %% "jwt-zio-json" % "9.4.5",
  "com.google.cloud" % "google-cloud-aiplatform" % "3.32.0"
)
