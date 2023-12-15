package com.github.fermorg.radgiver

import com.github.fermorg.radgiver.config.{DeichmanApiConfig, VertexAIConfig}
import com.github.fermorg.radgiver.http.HttpHandler
import com.github.fermorg.radgiver.service.{DeichmanApiService, VertexAIService}
import zio.config.typesafe.TypesafeConfigProvider
import zio.http.{Client, Server}
import zio.{ConfigProvider, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

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
          DeichmanApiService.layer,
          ZLayer.fromZIO(ZIO.config(DeichmanApiConfig.config)),
          Client.default,
          ZLayer.succeed(scope),
        )
    }
