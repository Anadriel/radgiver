package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.StateConfig
import zio.{RLayer, ZIO, ZLayer}

trait StateService {
  def getState: ZIO[Any, Throwable, Set[String]]

  def setState(newState: Set[String]): ZIO[Any, Throwable, Unit]
}

object StateService {

  private class LiveStateService(gcs: GcsService, config: StateConfig) extends StateService {

    override def getState: ZIO[Any, Throwable, Set[String]] =
      gcs.readBytes(config.defaultPath).map {
        case None =>
          Set.empty[String]
        case Some(bytes) =>
          new String(bytes, "UTF-8")
            .split(config.delimiter)
            .toSet
      }

    override def setState(newState: Set[String]): ZIO[Any, Throwable, Unit] = {
      val stateBytes = newState
        .reduce(_ + config.delimiter + _)
        .getBytes("UTF-8")
      gcs.writeBytes(config.defaultPath, stateBytes)
    }

  }

  val layer: RLayer[GcsService with StateConfig, StateService] = ZLayer {
    for {
      gcs <- ZIO.service[GcsService]
      config <- ZIO.service[StateConfig]
    } yield LiveStateService(gcs, config)
  }

}
