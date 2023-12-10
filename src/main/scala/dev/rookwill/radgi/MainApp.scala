package dev.rookwill.radgi

import dev.rookwill.radgi.http.*
import zio.*
import zio.http.*

object MainApp extends ZIOAppDefault:

  def run: ZIO[Environment with ZIOAppArgs with Scope, Throwable, Any] =

    val httpApps = EventHandler()

    for
      server <- Server
        .serve(
          httpApps.withDefaultErrorResponse
        )
        .provide(
          Server.defaultWithPort(8080)
        )
    yield server

