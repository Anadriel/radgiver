package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.PredictorConfig
import com.github.fermorg.radgiver.model.http.PromptedPrediction
import com.github.fermorg.radgiver.service.DeichmanApiService.TimeWindow

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

    def getNewPredictions(
      horizonDays: Option[Int],
      batchSize: Option[Int],
    ): ZIO[Scope, Throwable, List[PromptedPrediction]] = {
      val realHorizonDays = horizonDays.getOrElse(config.defaultPlanningHorizonDays)
      val realBatchSize = batchSize.getOrElse(config.defaultBatchSize)

      for {
        processedEventsIds <- state.getState
        fetchedEvents <- deichman.eventRefs(
          timeWindow = TimeWindow.nextN(realHorizonDays),
          limit = None,
        )
        fetchedEventIds = fetchedEvents.map(_.id).toSet
        _ <- ZIO.logInfo(
          s"Fetched ${fetchedEventIds.size} events: ${fetchedEventIds.mkString("[", ", ", "]")}"
        )
        eventsForProcessing = fetchedEvents
          .filterNot(event => processedEventsIds.contains(event.id))
          .sortBy(_.startTime)
          .take(realBatchSize)
          .map(_.id)
        _ <- ZIO.logInfo(
          s"Processing ${eventsForProcessing.size} events: ${eventsForProcessing.mkString("[", ", ", "]")}"
        )
        nextState = processedEventsIds.intersect(fetchedEventIds).union(eventsForProcessing.toSet)
        result <- predictionsAboveThreshold(eventsForProcessing)
        _ <- ZIO.logInfo(s"Got ${result.size} predictions above threshold")
        _ <- ZIO.logInfo(
          s"Next state (${nextState.size} events): ${nextState.mkString("[", ", ", "]")}"
        )
        _ <- state.setState(nextState)
      } yield result
    }

    private def predictionsAboveThreshold(
      eventIds: Chunk[String]
    ): ZIO[Scope, Throwable, List[PromptedPrediction]] = eventIds
      .mapZIOPar { eventId =>
        deichman
          .getEvent(eventId)
          .flatMap(event => vertexAI.predictChatPrompt(event.description))
          .tap(prediction =>
            ZIO.logInfo(
              s"Event $eventId processed with the result: ${prediction.fold("No prediction")(_.toJson)}"
            )
          )
      }
      .map {
        _.flatMap {
          case Some(promptedPrediction)
              if promptedPrediction.percentage >= config.minimalEventMatchProbability =>
            Some(promptedPrediction)
          case _ =>
            None
        }.toList
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
