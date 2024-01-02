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
  "dev.zio" %% "zio-streams" % "2.0.20",
  "dev.zio" %% "zio-json" % "0.6.2",
  "dev.zio" %% "zio-http" % "3.0.0-RC4",
  "dev.zio" %% "zio-config" % "4.0.0",
  "dev.zio" %% "zio-config-typesafe" % "4.0.0",
  "dev.zio" %% "zio-config-magnolia" % "4.0.0",
  "com.softwaremill.quicklens" %% "quicklens" % "1.9.6",
  "com.github.jwt-scala" %% "jwt-zio-json" % "9.4.5",
  "com.google.cloud" % "google-cloud-storage" % "2.30.1",
  "com.google.cloud" % "google-cloud-aiplatform" % "3.33.0",
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
