package dev.rookwill.radgi.service

import dev.rookwill.radgi.config.DeichmanApiConfig
import dev.rookwill.radgi.model.deichman.{EventInfo, EventRef}
import zio.http.{Client, Request, URL, ZClient}
import zio.{Chunk, RLayer, ZIO, ZLayer}
import zio.stream.ZStream
import zio.json.ast.Json
import zio.json.*

trait DeichmanApiService {
  def eventRefs(from: Int): ZStream[Any, Throwable, EventRef]
  def getEvent(id: String): ZIO[Any, Throwable, EventInfo]
}

object DeichmanApiService {

  private class LiveDeichmanApiService(
    client: Client,
    endpoint: URL
  ) extends DeichmanApiService {

    private def extractEventRefs(data: String): Option[Chunk[EventRef]] =
      for
        mainObj <- data.fromJson[Json.Obj].toOption
        searchResults <- mainObj.get("searchResults")
        eventRefsArray <- searchResults.asArray
        chunk = eventRefsArray.flatMap(er => er.as[EventRef].toOption)
      yield chunk

    def eventRefs(from: Int = 0): ZStream[Any, Throwable, EventRef] = {
      val eventRefChunk = for {
        res <- client.request(
          Request.get(
            endpoint.withQueryParams("from" -> Chunk.single(from.toString))
          )
        )
        resBody <- res.body.asString
        eventRef <- ZIO
          .fromOption(extractEventRefs(resBody))
          .mapError(_ => new Exception("Couldn't extract events"))
      } yield eventRef
      ZStream.fromZIO(eventRefChunk).flatMap(ZStream.fromChunk(_))
    }

    def getEvent(id: String): ZIO[Any, Throwable, EventInfo] =
      for {
        res <- client.request(Request.get(URL.empty.withPath(endpoint.path / id)))
        resBody <- res.body.asString
        eventInfo <- ZIO
          .fromEither(resBody.fromJson[EventInfo])
          .mapError(err => new Exception(err))
      } yield eventInfo

  }

  val layer: RLayer[DeichmanApiConfig with Client, DeichmanApiService] =
    ???

}
