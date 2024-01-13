package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.deriveConfig

case class StateConfig(defaultPath: String, delimiter: String)

object StateConfig {

  val config: Config[StateConfig] = deriveConfig[StateConfig]
    .nested("state")
    .validate("Invalid state config") { config =>
      config.defaultPath.nonEmpty && config.delimiter.nonEmpty
    }

  val layer: Layer[Config.Error, StateConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
