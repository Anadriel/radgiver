package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.{deriveConfig, DeriveConfig}

import java.time.ZoneId
import scala.util.{Failure, Success, Try}

case class PredictorConfig(
  defaultPlanningHorizonDays: Int,
  defaultBatchSize: Int,
  timeZone: ZoneId,
  minimalEventMatchProbability: Int,
)

object PredictorConfig {

  given deriveURL: DeriveConfig[ZoneId] = DeriveConfig[String].mapOrFail { s =>
    Try(ZoneId.of(s)) match
      case Failure(value) => Left(Config.Error.InvalidData(message = value.getMessage))
      case Success(value) => Right(value)
  }

  val config: Config[PredictorConfig] =
    deriveConfig[PredictorConfig].nested("predictor")

  val layer: Layer[Config.Error, PredictorConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
