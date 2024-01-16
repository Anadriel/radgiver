package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.config.PredictorConfig
import com.github.fermorg.radgiver.model.deichman.EventRef
import com.github.fermorg.radgiver.model.http.PromptedPrediction
import nl.vroste.rezilience.RateLimiter

import java.time.ZonedDateTime
import zio.{durationInt, Chunk, RLayer, Scope, ZIO, ZLayer}
import zio.json.*

trait PredictorService {

  def getNewPredictions(
    horizonDays: Option[Int],
    batchSize: Option[Int],
  ): ZIO[Scope, Throwable, Set[PromptedPrediction]]

}

object PredictorService {

  private class LivePredictorService(
    deichman: DeichmanApiService,
    state: StateService,
    vertexAI: VertexAIService,
    config: PredictorConfig,
  ) extends PredictorService {

    private lazy val rateLimiter = RateLimiter.make(config.vertexAIQuota, 60.second)

    private def getPredictionFor(id: String): ZIO[Scope, Throwable, Option[PromptedPrediction]] = {
      lazy val predictionRetrieving = for {
        event <- deichman.getEvent(id)
        prediction <- vertexAI.predictChatPrompt(event.description)
        _ <- ZIO.logInfo(s"Event $id was proceed with the result: ${prediction.map(_.toJson)}")
      } yield prediction
      def x = predictionRetrieving.catchAll { e =>
        ZIO.logErrorCause(zio.Cause.fail(e)).map(_ => None)
      }
      rateLimiter.flatMap(rl => rl(x))
    }

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
    ): ZIO[Scope, Throwable, Set[PromptedPrediction]] = {
      val realHorizonDays = horizonDays.getOrElse(config.defaultPlanningHorizonDays)
      val realBatchSize = batchSize.getOrElse(config.defaultBatchSize)

      for {
        processedEventsIds <- state.getState
        fetchedEvents <- deichman.eventRefs
        eventsForProceeding = getEventsIdsForHorizon(
          fetchedEvents,
          processedEventsIds,
          realHorizonDays,
        )
        newStateBase = fetchedEvents.map(_.id).toSet.intersect(processedEventsIds)
        _ <- ZIO.logInfo(s"Events inside in horizon: ${eventsForProceeding.size}")
        stateAndPredictions <- extractPredictions(
          eventsForProceeding,
          newStateBase,
          Set.empty,
          0,
          realBatchSize,
        )
        _ <- state.setState(stateAndPredictions._1)
      } yield stateAndPredictions._2
    }

    private def extractPredictions(
      eventIds: Chunk[String],
      state: Set[String],
      predictions: Set[PromptedPrediction],
      total: Int,
      batchSize: Int,
    ): ZIO[Scope, Throwable, (Set[String], Set[PromptedPrediction])] =
      if (eventIds.isEmpty || total >= batchSize)
        ZIO.succeed(state, predictions)
      else {
        val eventId = eventIds.head
        getPredictionFor(eventId).flatMap {
          case Some(p) =>
            if (p.percentage >= config.minimalEventMatchProbability)
              extractPredictions(
                eventIds.tail,
                state + eventId,
                predictions + p,
                total + 1,
                batchSize,
              )
            else
              extractPredictions(eventIds.tail, state + eventId, predictions, total, batchSize)
          case None =>
            extractPredictions(eventIds.tail, state, predictions, total, batchSize)
        }
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
