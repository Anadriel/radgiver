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
        .status(Status.BadRequest)
    )
  }

  private val radApp = Routes(
    Method.GET / "trigger" -> handler {
      ZIO
        .service[RadService]
        .flatMap(_.getNewRader)
        .map(rader => Response.json(rader.toJson))
    }
  )

  private val predictApp = Routes(
    Method.POST / "predict" -> handler { (request: Request) =>
      val jsonBody = request.body.asString.map(_.fromJson[Json.Obj])
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
                  .status(Status.BadRequest)
              )
            case Some(m) =>
              ZIO.serviceWithZIO[VertexAIService](_.predictChatPrompt(m)).map {
                case Some(prediction) =>
                  Response.json(prediction.toJson)
                case None =>
                  val msg = "Unable to predict anything"
                  Response
                    .json(ErrorResponse(msg, Status.InternalServerError.code).toJson)
                    .status(Status.InternalServerError)
              }
      }
    }
  )

  private val eventsApp = Routes(
    Method.GET / "events" -> handler {
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
    },
    Method.GET / "events" / string("id") -> handler { (id: String, _: Request) =>
      ZIO
        .service[DeichmanApiService]
        .flatMap(_.getEvent(id))
        .map(event => Response.json(event.toJson))
    },
  )

  private val blobsApp = Routes(
    Method.POST / "blobs" -> handler { (request: Request) =>
      for {
        jsonBody <- request.body.asString.map(_.fromJson[WriteBlob])
        response <- jsonBody match {
          case Left(e) =>
            composeFailedInputResponse(e)
          case Right(blob) =>
            ZIO
              .service[GcsService]
              .flatMap(_.writeBytes(blob.path, blob.content.getBytes(UTF_8), blob.overwrite))
              .map(_ => Response.ok)
        }
      } yield response
    },
    Method.GET / "blobs" / string("path") -> handler { (path: String, _: Request) =>
      ZIO
        .service[GcsService]
        .flatMap(_.readBytes(path))
        .map {
          case None =>
            Response.status(Status.NotFound)
          case Some(bytes) =>
            Response.json(BlobContent(String(bytes, UTF_8)).toJson)
        }
    },
  )

  val routes: HttpApp[RadService with GcsService with DeichmanApiService with VertexAIService] =
    (radApp ++ predictApp ++ eventsApp ++ blobsApp)
      .handleError(e =>
        Response.json(ErrorResponse.fromError(e).toJson).status(Status.InternalServerError)
      )
      .toHttpApp
