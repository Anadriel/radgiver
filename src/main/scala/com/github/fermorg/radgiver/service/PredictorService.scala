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
          .map(_.id)
        _ <- ZIO.logInfo(
          s"Available for processing ${eventsForProcessing.size} events: ${eventsForProcessing
              .mkString("[", ", ", "]")}"
        )
        result <- predictionsAndNewState(eventsForProcessing, realBatchSize)
        nextState = processedEventsIds.intersect(fetchedEventIds).union(result._2.toSet)
        _ <- ZIO.logInfo(s"Got ${result._1.size} predictions above threshold")
        _ <- ZIO.logInfo(
          s"Next state (${nextState.size} events): ${nextState.mkString("[", ", ", "]")}"
        )
        _ <- state.setState(nextState)
      } yield result._1
    }

    private def predictionsAndNewState(
      newEventIds: Chunk[String],
      realBatchSize: Int,
      resultedPredictions: List[PromptedPrediction] = List.empty,
      processedEvents: List[String] = List.empty,
    ): ZIO[Any, Throwable, (List[PromptedPrediction], List[String])] =
      newEventIds.headOption match {
        case Some(eventId) if resultedPredictions.size < realBatchSize =>
          eventProcessing(eventId).flatMap { op =>
            predictionsAndNewState(
              newEventIds.tail,
              realBatchSize,
              op.fold(resultedPredictions)(p => resultedPredictions :+ p),
              processedEvents :+ eventId,
            )
          }
        case _ =>
          ZIO.succeed((resultedPredictions, processedEvents))
      }

    private def eventProcessing(eventId: String): ZIO[Any, Throwable, Option[PromptedPrediction]] =
      deichman
        .getEvent(eventId)
        .flatMap(event => vertexAI.predictChatPrompt(event.description))
        .tap { prediction =>
          ZIO.logInfo(
            s"Event $eventId processed with the result: ${prediction.fold("No prediction")(_.toJson)}"
          )
        }
        .map {
          case Some(promptedPrediction)
              if promptedPrediction.percentage >= config.minimalEventMatchProbability =>
            Some(promptedPrediction)
          case _ =>
            None
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
