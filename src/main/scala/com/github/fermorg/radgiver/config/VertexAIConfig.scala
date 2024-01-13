package com.github.fermorg.radgiver.config

import zio.{Config, Layer, ZIO, ZLayer}
import zio.config.magnolia.deriveConfig

case class VertexAIConfig(
  project: String,
  location: String,
  apiEndpoint: String,
  publisher: String,
  model: String,
  promptRef: String,
) {
  def regionalEndpoint: String = s"$location-$apiEndpoint"
}

object VertexAIConfig {

  val config: Config[VertexAIConfig] = deriveConfig[VertexAIConfig]
    .nested("vertex-ai")
    .validate("Invalid VertexAI config") { config =>
      config.project.nonEmpty &&
      config.location.nonEmpty &&
      config.apiEndpoint.nonEmpty &&
      config.publisher.nonEmpty &&
      config.model.nonEmpty && config.promptRef.nonEmpty
    }

  val layer: Layer[Config.Error, VertexAIConfig] =
    ZLayer.fromZIO(ZIO.config(config))

}
