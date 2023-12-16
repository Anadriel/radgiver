package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.DeichmanApiConfig
import com.github.fermorg.radgiver.model.deichman.{EventInfo, EventRef}
import zio.http.{Client, Request, ZClient}
import zio.{Chunk, RLayer, Task, ZIO, ZLayer}
import zio.stream.ZStream
import zio.json.ast.Json
import zio.json.*

trait DeichmanApiService {
  def eventRefs: ZIO[Any, Throwable, Chunk[EventRef]]
  def getEvent(id: String): ZIO[Any, Throwable, EventInfo]
}

object DeichmanApiService {

  private class LiveDeichmanApiService(
    client: Client,
    config: DeichmanApiConfig,
  ) extends DeichmanApiService {

    private def getTotalAndEventsAt(offset: Int): Task[Option[(Int, Chunk[EventRef])]] = {
      val request =
        Request.get(config.eventsEndpoint.withQueryParams("from" -> Chunk.single(offset.toString)))
      for {
        res <- client.request(request)
        resBody <- res.body.asString
      } yield LiveDeichmanApiService.extractEventRefsAndTotal(resBody)
    }

    def eventRefs: ZIO[Any, Throwable, Chunk[EventRef]] = getTotalAndEventsAt(0).flatMap {
      case None => ZIO.succeed(Chunk.empty)
      case Some((total, firstPage)) =>
        val pageSize = firstPage.length
        val allChunks = ZStream
          .unfold(0) { current =>
            val next = current + pageSize
            if (next >= total) None else Some((next, next))
          }
          .flatMapPar(config.parallelism) { offset =>
            ZStream.fromZIO(getTotalAndEventsAt(offset)).mapConcatChunk {
              case Some((_, chunk)) => chunk
              case None             => Chunk.empty
            }
          }
          .runCollect
          .map(_ ++ firstPage)
        allChunks.flatMap {
          case chunks if chunks.length == total =>
            ZIO.succeed(chunks)
          case chunks =>
            ZIO.fail(new Exception(s"Expected $total events, got ${chunks.length}"))
        }
    }

    def getEvent(id: String): ZIO[Any, Throwable, EventInfo] = {
      val url = config.eventsEndpoint.withPath(config.eventsEndpoint.path / id)
      for {
        res <- client.request(Request.get(url))
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

  val layer: RLayer[DeichmanApiConfig with Client, DeichmanApiService] = ZLayer {
    for {
      config <- ZIO.service[DeichmanApiConfig]
      client <- ZIO.service[Client]
    } yield LiveDeichmanApiService(client, config)
  }

}
