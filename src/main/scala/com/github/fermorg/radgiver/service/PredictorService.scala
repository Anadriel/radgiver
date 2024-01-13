package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.PredictorConfig
import com.github.fermorg.radgiver.model.deichman.EventRef
import com.github.fermorg.radgiver.model.http.PromptedPrediction

import java.time.ZonedDateTime
import zio.{Chunk, Duration, RLayer, ZIO, ZLayer}
import zio.json.*

trait PredictorService {

  def getNewPredictions(
    horizonDays: Option[Int],
    batchSize: Option[Int],
  ): ZIO[Any, Throwable, Set[PromptedPrediction]]

}

object PredictorService {

  private class LivePredictorService(
    deichman: DeichmanApiService,
    state: StateService,
    vertexAI: VertexAIService,
    config: PredictorConfig,
  ) extends PredictorService {

    private def getPredictionFor(id: String): ZIO[Any, Throwable, Option[PromptedPrediction]] = {
      val sleepTimeSeconds = Math.ceil(60d / config.vertexAIQuota).toInt
      val predictionRetrieving = for {
        _ <- ZIO.sleep(Duration.fromSeconds(sleepTimeSeconds))
        event <- deichman.getEvent(id)
        prediction <- vertexAI.predictChatPrompt(event.description)
        _ <- ZIO.logInfo(s"Event $id was proceed with the result: ${prediction.map(_.toJson)}")
      } yield prediction
      predictionRetrieving.catchAll { e =>
        ZIO.logErrorCause(zio.Cause.fail(e)).map(_ => None)
      }
    }

    private def getEventsIdsForHorizon(
      events: Chunk[EventRef],
      processedEventsIds: Set[String],
      horizonDays: Int,
      batchSize: Int,
    ): Chunk[String] = {
      val now = ZonedDateTime.now(config.timeZone)
      val horizonsEnd = now.plusDays(horizonDays)
      val eventsInHorizon = events
        .filter(e => !processedEventsIds.contains(e.id) && e.startTime.isBefore(horizonsEnd))
        .sortBy(_.startTime)
        .map(_.id)
      val eventsInHorizonAmount = eventsInHorizon.size
      println(s"Events inside in horizon: $eventsInHorizonAmount")
      if (batchSize < eventsInHorizonAmount) eventsInHorizon.take(batchSize) else eventsInHorizon
    }

    def getNewPredictions(
      horizonDays: Option[Int],
      batchSize: Option[Int],
    ): ZIO[Any, Throwable, Set[PromptedPrediction]] = {
      val realHorizonDays = horizonDays.getOrElse(config.defaultPlanningHorizonDays)
      val realBatchSize = batchSize.getOrElse(config.defaultBatchSize)

      for {
        processedEventsIds <- state.getState
        fetchedEvents <- deichman.eventRefs
        eventsForProceeding = getEventsIdsForHorizon(
          fetchedEvents,
          processedEventsIds,
          realHorizonDays,
          realBatchSize,
        )
        newStateBase = fetchedEvents.map(_.id).toSet.intersect(processedEventsIds)
        _ <- ZIO.logInfo(s"Need to get predictions for ${eventsForProceeding.size} events")
        stateAndPredictions <- ZIO.foldLeft(eventsForProceeding)(
          (newStateBase, Set.empty[PromptedPrediction])
        ) { case ((state, result), eventId) =>
          getPredictionFor(eventId)
            .map(_.map(p => (state + eventId, result + p)).getOrElse((state, result)))
        }
        _ <- state.setState(stateAndPredictions._1)
      } yield stateAndPredictions._2
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
