package dev.rookwill.radgi.http

import zio.{ZIO, http}
import zio.http.*
import zio.json.ast.Json
import zio.json.*
import dev.rookwill.radgi.service.VertexAIService

object HttpHandler:

  val routes: Http[VertexAIService, Throwable, Request, Response] =
    Http.collectZIO[Request] { case r @ Method.POST -> Root / "verdict" =>
      val jsonBody = r.body.asString.map(_.fromJson[Json.Obj])
      val response = jsonBody.flatMap {
        case Left(e) =>
          val msg = s"Failed to parse the input: $e"
          ZIO
            .debug(msg)
            .as(Response.text(msg).withStatus(Status.BadRequest))
        case Right(json) =>
          json.get("message").flatMap(_.asString) match
            case None =>
              val msg = "Couldn't find value 'message'"
              ZIO
                .debug(msg)
                .as(Response.text(msg).withStatus(Status.BadRequest))
            case Some(m) =>
              ZIO
                .serviceWithZIO[VertexAIService](_.predictChatPrompt(m))
                .map {
                  case Some(predictionContent) =>
                    Response.text(predictionContent)
                  case None =>
                    Response.text("No prediction").withStatus(Status.Accepted)
                }
      }
      response.orDie
    }
