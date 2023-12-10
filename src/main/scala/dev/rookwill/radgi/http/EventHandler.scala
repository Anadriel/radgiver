package dev.rookwill.radgi.http

import zio.{ZIO, http}
import zio.http.*
import zio.json.ast.Json
import zio.stream.ZStream
import zio.json.*

object EventHandler:

  def apply(): Http[Any, Throwable, Request, Response] =
    Http.collectZIO[Request] {
      // POST /verdict
      case r@Method.POST -> Root / "verdict" =>
        val makeResponse = for
          body <- r.body.asString.map(_.fromJson[Json.Obj])
          response <- body match
            case Left(e) =>
              ZIO
                .debug(s"Failed to parse the input: $e")
                .as(
                  Response.text(e).withStatus(Status.BadRequest)
                )
            case Right(json) =>
              json.get("message").flatMap(_.asString) match
                case None =>
                  val e = "Couldn't find value 'message'"
                  ZIO
                    .debug(e)
                    .as(
                      Response.text(e).withStatus(Status.BadRequest)
                    )
                case Some(m) =>
                  VertexAI
                    .request(m)
                    .map(r => Response.text(r))
        yield response
        makeResponse.orDie
    }
