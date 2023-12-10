scalaVersion := "3.3.1"

name := "radgi"
organization := "anna.rookwill"
version := "1.0"

libraryDependencies ++= Seq(
  "dev.zio"                  %% "zio"            % "2.0.19",
  "dev.zio"                  %% "zio-json"       % "0.6.2",
  "dev.zio"                  %% "zio-http"       % "3.0.0-RC2",
  "io.getquill"              %% "quill-zio"      % "4.7.0",
  "io.getquill"              %% "quill-jdbc-zio" % "4.7.0",
  "com.h2database"           %  "h2"             % "2.2.224",
  "com.softwaremill.quicklens" %% "quicklens"    % "1.9.6",
  "com.github.jwt-scala"     %% "jwt-zio-json"   % "9.4.5"
)
