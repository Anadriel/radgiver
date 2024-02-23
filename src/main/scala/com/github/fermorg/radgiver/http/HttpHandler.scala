package com.github.fermorg.radgiver.http

import com.github.fermorg.radgiver.model.http.{BlobContent, ErrorResponse, WriteBlob}
import zio.{http, Scope, ZIO}
import zio.http.*
import zio.json.ast.Json
import zio.json.*
import com.github.fermorg.radgiver.service.*
import com.github.fermorg.radgiver.service.DeichmanApiService.TimeWindow

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

  private val adviserApp = Routes(
    Method.GET / "trigger" -> handler { (request: Request) =>
      val horizon = request.url.queryParams.getAs[Int]("horizon").toOption
      val batch = request.url.queryParams.getAs[Int]("batch").toOption
      ZIO
        .service[PredictorService]
        .flatMap(_.getNewPredictions(horizon, batch))
        .map(advises => Response.json(advises.toJson))
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
    Method.GET / "events" -> handler { (request: Request) =>
      val limit = request.url.queryParams.getAs[Int]("limit").toOption
      val horizon = request.url.queryParams.getAs[Int]("horizon").toOption

      ZIO
        .service[DeichmanApiService]
        .flatMap(_.eventRefs(timeWindow = horizon.flatMap(TimeWindow.nextN), limit = limit))
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

  val routes: HttpApp[
    PredictorService & GcsService & DeichmanApiService & VertexAIService & Scope
  ] =
    (adviserApp ++ predictApp ++ eventsApp ++ blobsApp)
      .handleError(e =>
        Response.json(ErrorResponse.fromError(e).toJson).status(Status.InternalServerError)
      )
      .toHttpApp
