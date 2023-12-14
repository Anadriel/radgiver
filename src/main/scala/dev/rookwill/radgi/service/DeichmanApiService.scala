package dev.rookwill.radgi.service

import dev.rookwill.radgi.config.DeichmanApiConfig
import dev.rookwill.radgi.model.deichman.{EventInfo, EventRef}
import zio.http.Client
import zio.{RLayer, ZIO}
import zio.stream.ZStream

trait DeichmanApiService {
  def eventRefs: ZStream[Any, Throwable, EventRef]
  def getEvent(id: String): ZIO[Any, Throwable, EventInfo]
}

object DeichmanApiService {

  val layer: RLayer[DeichmanApiConfig with Client, DeichmanApiService] =
    ???

}
