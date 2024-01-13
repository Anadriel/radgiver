package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.deriveConfig

case class GcsConfig(project: String, bucketName: String)

object GcsConfig {

  val config: Config[GcsConfig] = deriveConfig[GcsConfig]
    .nested("google-cloud-storage")
    .validate("Invalid GCS config") { config =>
      config.project.nonEmpty && config.bucketName.nonEmpty
    }

  val layer: Layer[Config.Error, GcsConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
