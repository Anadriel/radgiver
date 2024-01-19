package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.{deriveConfig, DeriveConfig}

case class PredictorConfig(
  defaultPlanningHorizonDays: Int,
  defaultBatchSize: Int,
  minimalEventMatchProbability: Int,
)

object PredictorConfig {

  val config: Config[PredictorConfig] =
    deriveConfig[PredictorConfig].nested("predictor")

  val layer: Layer[Config.Error, PredictorConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
