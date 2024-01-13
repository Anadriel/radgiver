package com.github.fermorg.radgiver

import com.github.fermorg.radgiver.config.{
  DeichmanApiConfig,
  GcsConfig,
  PredictorConfig,
  StateConfig,
  VertexAIConfig,
}
import com.github.fermorg.radgiver.http.HttpHandler
import com.github.fermorg.radgiver.service.{
  DeichmanApiService,
  GcsService,
  PredictorService,
  StateService,
  VertexAIService,
}
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.Server.Config
import zio.http.netty.server.FixedServer
import zio.http.{Client, Server}
import zio.{ConfigProvider, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

object MainApp extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider
        .fromResourcePath()
        .orElse(ConfigProvider.defaultProvider)
    )

  private val configuredGcs =
    GcsConfig.layer >>> GcsService.layer

  private val configuredDeichmanApi =
    (Client.default ++ DeichmanApiConfig.layer) >>> DeichmanApiService.layer

  private val configuredVertexAI =
    VertexAIConfig.layer >>> VertexAIService.layer

  private val configuredState =
    (configuredGcs ++ StateConfig.layer) >>> StateService.layer

  private val configuredPredictor = (
    configuredDeichmanApi
      ++ configuredState
      ++ configuredVertexAI
      ++ PredictorConfig.layer
  ) >>> PredictorService.layer

  def run: ZIO[Environment with ZIOAppArgs with Scope, Throwable, Any] = ZIO
    .service[Server]
    .flatMap { server =>
      ZIO.debug(s"Server live at ${server.port}") *> ZIO.never
    }
    .provide(
      ZLayer.succeed(Config.default.port(8080)) >>> FixedServer.withApp(HttpHandler.routes),
      Scope.default,
      configuredGcs,
      configuredDeichmanApi,
      configuredVertexAI,
      configuredPredictor,
    )
