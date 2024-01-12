package com.github.fermorg.radgiver.config

import zio.Config
import zio.config.magnolia.{deriveConfig, DeriveConfig}

import java.time.ZoneId
import scala.util.{Failure, Success, Try}

case class RadConfig(
  defaultPlanningHorizonDays: Int,
  defaultBatchSize: Int,
  vertexAIQuota: Int,
  timeZone: ZoneId,
)

object RadConfig {

  given deriveURL: DeriveConfig[ZoneId] = DeriveConfig[String].mapOrFail { s =>
    Try(ZoneId.of(s)) match
      case Failure(value) => Left(Config.Error.InvalidData(message = value.getMessage))
      case Success(value) => Right(value)
  }

  val config: Config[RadConfig] =
    deriveConfig[RadConfig].nested("rad")

}
