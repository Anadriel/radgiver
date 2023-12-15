package com.github.fermorg.radgiver.http

import zio.{http, ZIO}
import zio.http.*
import zio.json.ast.Json
import zio.json.*
import com.github.fermorg.radgiver.service.*
import zio.http.Path.Segment

object HttpHandler:

  val routes: Http[VertexAIService with DeichmanApiService, Throwable, Request, Response] =
    Http.collectZIO[Request] {
      case r @ Method.POST -> Root / "verdict" =>
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
      case Method.GET -> Root / "events" =>
        ZIO.service[DeichmanApiService].map{ das =>
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.application.`json`)),
            body = Body.fromStream(das.eventRefs(0).debug.map(_.toJson))
          )
        }
      case r@Method.GET -> Root / "events" / string =>
        //TODO: fix this
        ZIO.service[DeichmanApiService].flatMap { das =>
          das.getEvent(r.url.fragment.get.raw).debug.map{ ei =>
            Response.json(ei.toJson)
          }
        }

    }
