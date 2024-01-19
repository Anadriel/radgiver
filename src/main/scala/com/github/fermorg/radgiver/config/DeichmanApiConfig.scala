package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.{deriveConfig, DeriveConfig}
import zio.http.URL

import java.time.ZoneId
import scala.util.{Failure, Success, Try}

case class DeichmanApiConfig(eventsEndpoint: URL, parallelism: Int, timeZone: ZoneId)

object DeichmanApiConfig {

  given deriveURL: DeriveConfig[URL] = DeriveConfig[String].mapOrFail(s =>
    URL.decode(s) match
      case Left(value)  => Left(Config.Error.InvalidData(message = value.getMessage))
      case Right(value) => Right(value)
  )

  given deriveTimeZone: DeriveConfig[ZoneId] = DeriveConfig[String].mapOrFail { s =>
    Try(ZoneId.of(s)) match
      case Failure(value) => Left(Config.Error.InvalidData(message = value.getMessage))
      case Success(value) => Right(value)
  }

  val config: Config[DeichmanApiConfig] =
    deriveConfig[DeichmanApiConfig].nested("deichman-api")

  val layer: Layer[Config.Error, DeichmanApiConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
