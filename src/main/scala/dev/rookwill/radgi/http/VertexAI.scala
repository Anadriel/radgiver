package dev.rookwill.radgi.http

import dev.rookwill.radgi.model.{AIRequest, AIResponse}
import com.softwaremill.quicklens.*
import dev.rookwill.radgi.gcp.GCPAuth
import zio.{Console, ZIO, ZLayer, http}
import zio.http.*
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver
import zio.stream.{ZPipeline, ZSink, ZStream}
import zio.json.*

object VertexAI:
  private val modelId = "chat-bison"
  private val projectId = "radgiver"
  private val apiEndpoint = "us-central1-aiplatform.googleapis.com"
  private val locationId = "us-central1"
  private val endpoint =
    s"https://$apiEndpoint/v1/projects/$projectId/locations/$locationId/publishers/google/models/$modelId:predict"
  
  private lazy val url = URL.decode(endpoint).toOption.get

  private def composeBody(message: String): http.Body =
    val request = for
      fileContent <- ZStream.fromResource("ai-request.json")
        .via(ZPipeline.utf8Decode).run(ZSink.foldLeft[String, String]("")(_ + _))
      aiRequest <- ZIO.fromEither(fileContent.fromJson[AIRequest]).mapError(Exception(_))
        .map(_.modify(_.instances.each.messages.each.content).setTo(message).toJson)
    yield aiRequest
    Body.fromStream(ZStream.fromZIO(request).via(ZPipeline.utf8Encode))

  private def extractContent(data: String): Option[String] = for
    aiResponse <- data.fromJson[AIResponse].toOption
    prediction <- aiResponse.predictions.headOption
    candidate <- prediction.candidates.headOption
  yield candidate.content

  private def ask(token: String, message: String): ZIO[Client, Throwable, String] =
    val headers: Headers =
      Headers(
        Header.Authorization.Bearer(token),
        Header.ContentType(MediaType("application", "json"))
      )

    val request = Request.post(composeBody(message), url).addHeaders(headers)

    for
      res <- ZClient.request(request)
      resString <- res.body.asString
      _ <- Console.printLine(s"RESPONSE BODY TTT: $resString")
      content <-
        ZIO
          .fromOption(extractContent(resString))
          .mapError(_ => Exception("Couldn't parse Vertex response"))
    yield content

  def request(message: String): ZIO[Any, Throwable, String] =
    val httpFlow = for
      token <- GCPAuth.getToken
      response <- ask(token, message)
    yield response
    httpFlow.provide(
      ZLayer.succeed(ZClient.Config.default),
      Client.customized,
      NettyClientDriver.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.default),
    )