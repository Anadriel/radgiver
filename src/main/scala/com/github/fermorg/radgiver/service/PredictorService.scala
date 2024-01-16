package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.PredictorConfig
import com.github.fermorg.radgiver.model.deichman.EventRef
import com.github.fermorg.radgiver.model.http.PromptedPrediction

import java.time.ZonedDateTime
import zio.{Chunk, RLayer, Scope, ZIO, ZLayer}
import zio.json.*

trait PredictorService {

  def getNewPredictions(
    horizonDays: Option[Int],
    batchSize: Option[Int],
  ): ZIO[Scope, Throwable, List[PromptedPrediction]]

}

object PredictorService {

  private class LivePredictorService(
    deichman: DeichmanApiService,
    state: StateService,
    vertexAI: VertexAIService,
    config: PredictorConfig,
  ) extends PredictorService {

    private def getEventsIdsForHorizon(
      events: Chunk[EventRef],
      processedEventsIds: Set[String],
      horizonDays: Int,
    ): Chunk[String] = {
      val now = ZonedDateTime.now(config.timeZone)
      val horizonsEnd = now.plusDays(horizonDays)
      events
        .filter(e => !processedEventsIds.contains(e.id) && e.startTime.isBefore(horizonsEnd))
        .sortBy(_.startTime)
        .map(_.id)
    }

    def getNewPredictions(
      horizonDays: Option[Int],
      batchSize: Option[Int],
    ): ZIO[Scope, Throwable, List[PromptedPrediction]] = {
      val realHorizonDays = horizonDays.getOrElse(config.defaultPlanningHorizonDays)
      val realBatchSize = batchSize.getOrElse(config.defaultBatchSize)

      for {
        processedEventsIds <- state.getState
        fetchedEvents <- deichman.eventRefs
        eventsForProcessing = getEventsIdsForHorizon(
          fetchedEvents,
          processedEventsIds,
          realHorizonDays,
        )
        fetchedEventIds = fetchedEvents.map(_.id).toSet
        nextState = processedEventsIds.intersect(fetchedEventIds).union(eventsForProcessing.toSet)
        _ <- ZIO.logInfo(s"New events inside in horizon: ${eventsForProcessing.size}")
        result <- topNPredictions(eventsForProcessing, realBatchSize)
        _ <- state.setState(nextState)
      } yield result
    }

    private def topNPredictions(
      eventIds: Chunk[String],
      n: Int,
    ): ZIO[Scope, Throwable, List[PromptedPrediction]] = eventIds
      .mapZIOPar { eventId =>
        deichman
          .getEvent(eventId)
          .flatMap(event => vertexAI.predictChatPrompt(event.description))
          .tap(prediction =>
            ZIO.logDebug(
              s"Event $eventId was proceed with the result: ${prediction.map(_.toJson)}"
            )
          )
      }
      .map {
        _.flatMap {
          case Some(promptedPrediction)
              if promptedPrediction.percentage >= config.minimalEventMatchProbability =>
            Some(promptedPrediction)
          case _ => None
        }.sortBy(_.percentage)(Ordering[Int].reverse).take(n).toList
      }

  }

  val layer: RLayer[
    DeichmanApiService with StateService with VertexAIService with PredictorConfig,
    PredictorService,
  ] =
    ZLayer {
      for {
        deichman <- ZIO.service[DeichmanApiService]
        state <- ZIO.service[StateService]
        vertexAI <- ZIO.service[VertexAIService]
        config <- ZIO.service[PredictorConfig]
      } yield LivePredictorService(deichman, state, vertexAI, config)
    }

}
