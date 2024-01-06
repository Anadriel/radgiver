package com.github.fermorg.radgiver.service

import com.github.fermorg.radgiver.model.http.PromptedPrediction
import zio.{RLayer, ZIO, ZLayer}

trait RadService {
  def getNewRader: ZIO[Any, Throwable, Set[PromptedPrediction]]
}

object RadService {

  private class LiveRadService(
    deichman: DeichmanApiService,
    state: StateService,
    vertexAI: VertexAIService,
  ) extends RadService {

    override def getNewRader: ZIO[Any, Throwable, Set[PromptedPrediction]] =
      for {
        processedEvents <- state.getState
        fetchedEvents <- deichman.eventRefs.map(_.map(_.id).toSet)
        _ <- state.setState(fetchedEvents)
        eventsToRetrieve = fetchedEvents.diff(processedEvents)
        _ <- ZIO.logInfo(s"Need to get predictions for ${eventsToRetrieve.size} events")
        predictions <- ZIO.collectAllPar(eventsToRetrieve.map(getPredictionFor))
      } yield predictions.flatten

    private def getPredictionFor(id: String): ZIO[Any, Throwable, Option[PromptedPrediction]] =
      for {
        event <- deichman.getEvent(id)
        prediction <- vertexAI.predictChatPrompt(event.description)
      } yield prediction

  }

  val layer: RLayer[DeichmanApiService with StateService with VertexAIService, RadService] =
    ZLayer {
      for {
        deichman <- ZIO.service[DeichmanApiService]
        state <- ZIO.service[StateService]
        vertexAI <- ZIO.service[VertexAIService]
      } yield LiveRadService(deichman, state, vertexAI)
    }

}
