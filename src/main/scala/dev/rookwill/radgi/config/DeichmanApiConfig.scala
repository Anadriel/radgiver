package dev.rookwill.radgi.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class DeichmanApiConfig(eventsEndpoint: String)

object DeichmanApiConfig {
  val config: Config[DeichmanApiConfig] =
    deriveConfig[DeichmanApiConfig].nested("deichman-api")
}
