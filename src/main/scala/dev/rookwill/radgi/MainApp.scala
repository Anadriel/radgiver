package dev.rookwill.radgi

import dev.rookwill.radgi.config.VertexAIConfig
import dev.rookwill.radgi.http.HttpHandler
import dev.rookwill.radgi.service.VertexAIService
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.*

object MainApp extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(
      TypesafeConfigProvider
        .fromResourcePath()
        .orElse(ConfigProvider.defaultProvider)
    )

  def run: ZIO[Environment with ZIOAppArgs with Scope, Throwable, Any] =
    ZIO.scope.flatMap { scope =>
      Server
        .serve(HttpHandler.routes.withDefaultErrorResponse)
        .provide(
          Server.defaultWithPort(8080),
          VertexAIService.layer,
          ZLayer.fromZIO(ZIO.config(VertexAIConfig.config)),
          ZLayer.succeed(scope),
        )
    }
