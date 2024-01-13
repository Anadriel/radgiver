package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.{deriveConfig, DeriveConfig}
import zio.http.URL

case class DeichmanApiConfig(eventsEndpoint: URL, parallelism: Int)

object DeichmanApiConfig {

  given deriveURL: DeriveConfig[URL] = DeriveConfig[String].mapOrFail(s =>
    URL.decode(s) match
      case Left(value)  => Left(Config.Error.InvalidData(message = value.getMessage))
      case Right(value) => Right(value)
  )

  val config: Config[DeichmanApiConfig] =
    deriveConfig[DeichmanApiConfig].nested("deichman-api")

  val layer: Layer[Config.Error, DeichmanApiConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
