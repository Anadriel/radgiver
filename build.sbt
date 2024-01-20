name := "radgiver"
organization := "com.github.fermorg"
scalaVersion := "3.3.1"

// Versioning
inThisBuild(
  Seq(
    dynverSeparator := "-"
  )
)

// Dependencies
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-streams" % "2.0.21",
  "dev.zio" %% "zio-json" % "0.6.2",
  "dev.zio" %% "zio-http" % "3.0.0-RC4",
  "dev.zio" %% "zio-config" % "4.0.1",
  "dev.zio" %% "zio-config-typesafe" % "4.0.1",
  "dev.zio" %% "zio-config-magnolia" % "4.0.1",
  "dev.zio" %% "zio-logging-slf4j2" % "2.2.2",
  "com.softwaremill.quicklens" %% "quicklens" % "1.9.7",
  "com.github.jwt-scala" %% "jwt-zio-json" % "10.0.0",
  "nl.vroste" %% "rezilience" % "0.9.4",
  "com.google.cloud" % "google-cloud-storage" % "2.34.0",
  "com.google.cloud" % "google-cloud-aiplatform" % "3.36.0",
  "org.slf4j" % "log4j-over-slf4j" % "2.0.12",
  "org.slf4j" % "jcl-over-slf4j" % "2.0.12",
  "org.slf4j" % "jul-to-slf4j" % "2.0.12",
  "ch.qos.logback" % "logback-classic" % "1.5.0",
)

// Docker
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}

enablePlugins(JavaAppPackaging, LauncherJarPlugin, DockerPlugin)

dockerBaseImage := "bellsoft/liberica-runtime-container:jre-21-slim-musl"

dockerRepository := sys.env.get("DOCKER_REGISTRY").map(_.stripSuffix("/"))
packageName := s"${name.value}-service"

dockerExposedPorts := List(8080)

dockerPermissionStrategy := DockerPermissionStrategy.None
dockerCommands := dockerCommands.value.filter {
  case Cmd("USER", _*) => false
  case _               => true
}

dockerEntrypoint := Seq(
  "java",
  "-jar",
  s"/opt/docker/lib/${(packageJavaLauncherJar / artifactPath).value.getName}",
)
makeBashScripts := Seq()
makeBatScripts := Seq()

dockerAlias := {
  if (isSnapshot.value) {
    dockerAlias.value.withTag(dockerAlias.value.tag.map(t => s"snapshot-$t"))
  } else {
    dockerAlias.value
  }
}

// Publishing
Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

stage := (Docker / stage).value
publishLocal := (Docker / publishLocal).value
publish := {
  if (dockerRepository.value.isDefined) {
    (Docker / publish).value
  } else {
    ()
  }
}
