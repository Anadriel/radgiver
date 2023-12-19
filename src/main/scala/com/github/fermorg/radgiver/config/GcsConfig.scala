package com.github.fermorg.radgiver.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class GcsConfig(project: String, bucketName: String)

object GcsConfig:

  val config: Config[GcsConfig] = deriveConfig[GcsConfig]
    .nested("google-cloud-storage")
    .validate("Invalid GCS config") { config =>
      config.project.nonEmpty && config.bucketName.nonEmpty
    }
