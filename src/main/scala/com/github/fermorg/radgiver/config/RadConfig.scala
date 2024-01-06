package com.github.fermorg.radgiver.config

import zio.Config
import zio.config.magnolia.{deriveConfig, DeriveConfig}

case class RadConfig(defaultPlanningHorizonDays: Int, defaultBatchSize: Int, vertexAIQuota: Int)

object RadConfig {

  val config: Config[RadConfig] =
    deriveConfig[RadConfig].nested("rad")

}
