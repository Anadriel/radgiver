package com.github.fermorg.radgiver.http

import com.github.fermorg.radgiver.model.http.{BlobContent, ErrorResponse, WriteBlob}
import zio.{http, ZIO}
import zio.http.*
import zio.json.ast.Json
import zio.json.*
import com.github.fermorg.radgiver.service.*

import java.nio.charset.StandardCharsets.UTF_8

object HttpHandler:

  private def composeFailedInputResponse(error: String): ZIO[Any, Nothing, Response] = {
    val msg = s"Failed to parse the input: $error"
    ZIO.succeed(
      Response
        .json(ErrorResponse(msg, Status.BadRequest.code).toJson)
        .withStatus(Status.BadRequest)
    )
  }

  val routes: HttpApp[VertexAIService with DeichmanApiService with GcsService, Response] = Http
    .collectZIO[Request] {
      case r @ Method.POST -> Root / "predict" =>
        val jsonBody = r.body.asString.map(_.fromJson[Json.Obj])
        jsonBody.flatMap {
          case Left(e) =>
            composeFailedInputResponse(e)
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

      case r @ Method.POST -> Root / "blobs" =>
        for {
          jsonBody <- r.body.asString.map(_.fromJson[WriteBlob])
          response <- jsonBody match {
            case Left(e) =>
              composeFailedInputResponse(e)
            case Right(blob) =>
              ZIO
                .service[GcsService]
                .flatMap(
                  _.writeBytes(blob.path, blob.content.getBytes(UTF_8), blob.overwrite)
                )
                .map(_ => Response.ok)
          }
        } yield response

      case Method.GET -> Root / "blobs" / path =>
        ZIO
          .service[GcsService]
          .flatMap(_.readBytes(path))
          .map {
            case None =>
              Response.status(Status.NotFound)
            case Some(bytes) =>
              Response.json(BlobContent(String(bytes, UTF_8)).toJson)
          }
    }
    .mapError { e =>
      Response.json(ErrorResponse.fromError(e).toJson).withStatus(Status.InternalServerError)
    }
