package com.github.fermorg.radgiver.service

import com.google.cloud.aiplatform.v1.{
  EndpointName,
  PredictionServiceClient,
  PredictionServiceSettings,
}
import com.google.protobuf.Value
import com.google.protobuf.util.JsonFormat
import com.softwaremill.quicklens.*
import com.github.fermorg.radgiver.config.VertexAIConfig
import com.github.fermorg.radgiver.model.http.PromptedPrediction
import com.github.fermorg.radgiver.model.vertexai.{Instance, Parameters, Prediction, Request}
import zio.json.*
import zio.json.ast.Json
import zio.stream.{ZPipeline, ZSink, ZStream}
import zio.{RLayer, Scope, Task, ZIO, ZLayer}

import scala.jdk.CollectionConverters.*

trait VertexAIService {
  def predictChatPrompt(message: String): Task[Option[PromptedPrediction]]

}

object VertexAIService {

  private class LiveVertexAIService(
    client: PredictionServiceClient,
    endpoint: EndpointName,
    ctx: Request,
  ) extends VertexAIService {

    def predictChatPrompt(message: String): Task[Option[PromptedPrediction]] = {
      val modifiedCtx = ctx
        .modify(_.instances.each.messages.each.content)
        .setTo(message)

      val response = ZIO
        .attemptBlocking(
          client
            .predict(
              endpoint,
              modifiedCtx.instances
                .map(LiveVertexAIService.mkInstanceValue)
                .asJava,
              LiveVertexAIService.mkParametersValue(modifiedCtx.parameters),
            )
        )
      response.flatMap {
        case r if r.getPredictionsCount >= 1 =>
          LiveVertexAIService
            .extractPredictionContent(r.getPredictions(0))
            .map(_.map(PromptedPrediction.apply))
        case _ =>
          ZIO.succeed(None)
      }
    }

  }

  private object LiveVertexAIService {
    private val parser = JsonFormat.parser
    private val printer = JsonFormat.printer

    private def mkInstanceValue(
      instance: Instance
    ): Value =
      val instanceValueBuilder = Value.newBuilder
      parser.merge(instance.toJson, instanceValueBuilder)
      instanceValueBuilder.build

    private def mkParametersValue(parameters: Parameters): Value =
      val parameterValueBuilder = Value.newBuilder
      parser.merge(parameters.toJson, parameterValueBuilder)
      parameterValueBuilder.build

    private def extractPredictionContent(v: Value): Task[Option[Json.Obj]] = ZIO
      .fromEither {
        printer
          .print(v)
          .fromJson[Prediction]
          .map(
            _.candidates.headOption
              .flatMap(_.content.trim.fromJson[Json.Obj].toOption)
          )
      }
      .mapError(err => new Exception(err))

  }

  val layer: RLayer[Scope with VertexAIConfig, VertexAIService] = ZLayer {
    for {
      config <- ZIO.service[VertexAIConfig]
      predictionServiceSettings = PredictionServiceSettings
        .newBuilder()
        .setEndpoint(config.regionalEndpoint)
        .build()
      client <- ZIO.fromAutoCloseable(
        ZIO.attemptBlocking(
          PredictionServiceClient.create(predictionServiceSettings)
        )
      )
      ctx <- ZStream
        .fromResource(config.promptRef)
        .via(ZPipeline.utf8Decode)
        .run(ZSink.mkString)
        .flatMap { str =>
          ZIO
            .fromEither(str.fromJson[Request])
            .mapError(err => new Exception(err))
        }

    } yield new LiveVertexAIService(
      client,
      EndpointName.ofProjectLocationPublisherModelName(
        config.project,
        config.location,
        config.publisher,
        config.model,
      ),
      ctx,
    )
  }

}
