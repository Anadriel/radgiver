package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.DeichmanApiConfig
import com.github.fermorg.radgiver.model.deichman.{EventInfo, EventRef}
import com.github.fermorg.radgiver.service.DeichmanApiService.TimeWindow
import zio.http.{Client, QueryParams, Request, URL, ZClient}
import zio.{Chunk, RLayer, Scope, Task, ZIO, ZLayer}
import zio.json.ast.Json
import zio.json.*

import java.time.temporal.ChronoUnit
import java.time.LocalDate

trait DeichmanApiService {

  def eventRefs(
    timeWindow: Option[TimeWindow],
    limit: Option[Int],
  ): Task[Chunk[EventRef]]

  def getEvent(id: String): Task[EventInfo]
}

object DeichmanApiService {

  case class TimeWindow(startDate: LocalDate, endDate: LocalDate)

  object TimeWindow {

    def nextN(days: Int): Option[TimeWindow] = if (days <= 0) {
      None
    } else {
      val today = LocalDate.now()
      Some(TimeWindow(today, today.plusDays(days - 1)))
    }

  }

  private class LiveDeichmanApiService(
    client: Client,
    scope: Scope,
    config: DeichmanApiConfig,
  ) extends DeichmanApiService {

    def eventRefs(
      timeWindow: Option[TimeWindow],
      limit: Option[Int],
    ): Task[Chunk[EventRef]] = {
      val base = LiveDeichmanApiService.baseUrl(config, timeWindow)

      def getTotalAndEventsAt(offset: Int): Task[Option[(Int, Chunk[EventRef])]] = {
        val request =
          Request.get(base.addQueryParams(QueryParams("from" -> Chunk.single(offset.toString))))
        for {
          res <- client.request(request).provide(ZLayer.succeed(scope))
          resBody <- res.body.asString
        } yield LiveDeichmanApiService.extractEventRefsAndTotal(resBody)
      }

      getTotalAndEventsAt(0).flatMap {
        case None => ZIO.succeed(Chunk.empty)
        case Some((total, firstPage)) =>
          val pageSize = firstPage.length
          val offsets = List.unfold(0) { current =>
            val next = current + pageSize
            if (next >= total || limit.exists(_ <= next)) None else Some((next, next))
          }

          val restOfChunks = offsets.map(offset =>
            getTotalAndEventsAt(offset).map {
              case Some((_, chunk)) => chunk
              case None             => Chunk.empty
            }
          )

          val expected = limit match {
            case Some(l) if l < total =>
              (l.toDouble / pageSize).ceil.toInt * pageSize
            case _ => total
          }

          ZIO
            .reduceAllPar(ZIO.succeed(firstPage), restOfChunks)(_ ++ _)
            .flatMap {
              case chunk if chunk.length == expected =>
                ZIO.succeed(limit.fold(chunk)(chunk.take))
              case chunk =>
                ZIO.fail(new Exception(s"Expected $expected events, got ${chunk.length}"))
            }
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

    private def baseUrl(config: DeichmanApiConfig, timeWindow: Option[TimeWindow]): URL =
      timeWindow match {
        case Some(timeWindow) =>
          config.eventsEndpoint.addQueryParams(
            QueryParams(
              "fromDate" -> Chunk.single(
                timeWindow.startDate
                  .atStartOfDay(config.timeZone)
                  .toInstant
                  .truncatedTo(ChronoUnit.MILLIS)
                  .toString
              ),
              "toDate" -> Chunk.single(
                timeWindow.endDate
                  .atStartOfDay(config.timeZone)
                  .toInstant
                  .truncatedTo(ChronoUnit.MILLIS)
                  .toString
              ),
            )
          )
        case None => config.eventsEndpoint
      }

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

  val layer: RLayer[DeichmanApiConfig & Client & Scope, DeichmanApiService] = ZLayer {
    for {
      config <- ZIO.service[DeichmanApiConfig]
      client <- ZIO.service[Client]
      scope <- ZIO.service[Scope]
    } yield LiveDeichmanApiService(client, scope, config)
  }

}
