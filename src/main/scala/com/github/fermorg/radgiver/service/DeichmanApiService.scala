package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.DeichmanApiConfig
import com.github.fermorg.radgiver.model.deichman.{EventInfo, EventRef}
import zio.http.{Client, QueryParams, Request, ZClient}
import zio.{Chunk, RLayer, Scope, Task, ZIO, ZLayer}
import zio.json.ast.Json
import zio.json.*

trait DeichmanApiService {
  def eventRefs: Task[Chunk[EventRef]]
  def getEvent(id: String): Task[EventInfo]
}

object DeichmanApiService {

  private class LiveDeichmanApiService(
    client: Client,
    scope: Scope,
    config: DeichmanApiConfig,
  ) extends DeichmanApiService {

    private def getTotalAndEventsAt(
      offset: Int
    ): Task[Option[(Int, Chunk[EventRef])]] = {
      val request = Request.get(
        config.eventsEndpoint.addQueryParams(QueryParams("from" -> Chunk.single(offset.toString)))
      )
      for {
        res <- client.request(request).provide(ZLayer.succeed(scope))
        resBody <- res.body.asString
      } yield LiveDeichmanApiService.extractEventRefsAndTotal(resBody)
    }

    def eventRefs: Task[Chunk[EventRef]] = getTotalAndEventsAt(0).flatMap {
      case None => ZIO.succeed(Chunk.empty)
      case Some((total, firstPage)) =>
        val pageSize = firstPage.length
        val offsets = List.unfold(0) { current =>
          val next = current + pageSize
          if (next >= total) None else Some((next, next))
        }

        val restOfChunks = offsets.map(offset =>
          getTotalAndEventsAt(offset).map {
            case Some((_, chunk)) => chunk
            case None             => Chunk.empty
          }
        )

        ZIO
          .reduceAllPar(ZIO.succeed(firstPage), restOfChunks)(_ ++ _)
          .flatMap {
            case chunks if chunks.length == total =>
              ZIO.succeed(chunks)
            case chunks =>
              ZIO.fail(new Exception(s"Expected $total events, got ${chunks.length}"))
          }
    }

    def getEvent(id: String): Task[EventInfo] = {
      val url = config.eventsEndpoint.addPath(id)
      for {
        res <- client.request(Request.get(url)).provide(ZLayer.succeed(scope))
        resBody <- res.body.asString
        eventInfo <- ZIO
          .fromEither(resBody.fromJson[EventInfo])
          .mapError(err => new Exception(err))
      } yield eventInfo
    }

  }

  private object LiveDeichmanApiService {

    private def extractEventRefsAndTotal(data: String): Option[(Int, Chunk[EventRef])] =
      for
        mainObj <- data.fromJson[Json.Obj].toOption
        searchResults <- mainObj.get("searchResults")
        total <- mainObj.get("total").flatMap(_.asNumber).map(_.value.intValue())
        eventRefsArray <- searchResults.asArray
        chunk = eventRefsArray.flatMap(_.as[EventRef].toOption)
        nonEmptyChunk <- if (chunk.isEmpty) None else Some(chunk)
      yield total -> nonEmptyChunk

  }

  val layer: RLayer[DeichmanApiConfig with Client with Scope, DeichmanApiService] = ZLayer {
    for {
      config <- ZIO.service[DeichmanApiConfig]
      client <- ZIO.service[Client]
      scope <- ZIO.service[Scope]
    } yield LiveDeichmanApiService(client, scope, config)
  }

}
