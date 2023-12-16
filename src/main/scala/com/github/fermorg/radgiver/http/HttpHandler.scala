package com.github.fermorg.radgiver.http

import com.github.fermorg.radgiver.model.http.ErrorResponse
import zio.{http, ZIO}
import zio.http.*
import zio.json.ast.Json
import zio.json.*
import com.github.fermorg.radgiver.service.*

object HttpHandler:

  val routes: HttpApp[VertexAIService with DeichmanApiService, Response] = Http
    .collectZIO[Request] {
      case r @ Method.POST -> Root / "predict" =>
        val jsonBody = r.body.asString.map(_.fromJson[Json.Obj])
        jsonBody.flatMap {
          case Left(e) =>
            val msg = s"Failed to parse the input: $e"
            ZIO.succeed(
              Response
                .json(ErrorResponse(msg, Status.BadRequest.code).toJson)
                .withStatus(Status.BadRequest)
            )
          case Right(json) =>
            json.get("message").flatMap(_.asString) match
              case None =>
                val msg = "Couldn't find value 'message'"
                ZIO.succeed(
                  Response
                    .json(ErrorResponse(msg, Status.BadRequest.code).toJson)
                    .withStatus(Status.BadRequest)
                )
              case Some(m) =>
                ZIO
                  .serviceWithZIO[VertexAIService](_.predictChatPrompt(m))
                  .map {
                    case Some(prediction) =>
                      Response.json(prediction.toJson)
                    case None =>
                      val msg = "Unable to predict anything"
                      Response
                        .json(ErrorResponse(msg, Status.InternalServerError.code).toJson)
                        .withStatus(Status.InternalServerError)
                  }
        }

      case Method.GET -> Root / "events" =>
        ZIO
          .service[DeichmanApiService]
          .flatMap(_.eventRefs)
          .map { events =>
            Response(
              status = Status.Ok,
              headers = Headers(Header.ContentType(MediaType.application.`json`)),
              body = Body.fromString(events.toJson),
            )
          }

      case Method.GET -> Root / "events" / id =>
        ZIO
          .service[DeichmanApiService]
          .flatMap(_.getEvent(id))
          .map(event => Response.json(event.toJson))
    }
    .mapError { e =>
      Response.json(ErrorResponse.fromError(e).toJson).withStatus(Status.InternalServerError)
    }
