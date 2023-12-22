package com.github.fermorg.radgiver

import com.github.fermorg.radgiver.config.{DeichmanApiConfig, GcsConfig, VertexAIConfig}
import com.github.fermorg.radgiver.http.HttpHandler
import com.github.fermorg.radgiver.service.{DeichmanApiService, GcsService, VertexAIService}
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

  def run: ZIO[Environment with ZIOAppArgs with Scope, Throwable, Any] = ZIO
    .service[Server]
    .flatMap { server =>
      ZIO.debug(s"Server live at ${server.port}") *> ZIO.never
    }
    .provide(
      FixedServer.withApp(HttpHandler.routes),
      ZLayer.succeed(Config.default.port(8080)),
      VertexAIService.layer,
      ZLayer.fromZIO(ZIO.config(VertexAIConfig.config)),
      DeichmanApiService.layer,
      ZLayer.fromZIO(ZIO.config(DeichmanApiConfig.config)),
      GcsService.layer,
      ZLayer.fromZIO(ZIO.config(GcsConfig.config)),
      Client.default,
      Scope.default,
    )
