package dev.rookwill.radgi.gcp

import zio.{Chunk, Console, ZIO}
import zio.http.{Body, Client, Request, URL, ZClient}
import zio.json.ast.Json
import zio.json._

object GCPAuth:

  private val endpoint = "https://oauth2.googleapis.com/token"
  private val validSecs = 3600L

  def getToken: ZIO[Client, Throwable, String] = for
    jwtToken <-JWTTokenGenerator.jwtToken(validSecs)
    params = Map(
      "grant_type" -> Chunk("urn:ietf:params:oauth:grant-type:jwt-bearer"),
      "assertion" -> Chunk(jwtToken)
    )
    url = URL.decode(endpoint).toOption.get.withQueryParams(params)
    request = Request.post(Body.empty, url)
    res <- ZClient.request(request)
    token <- res.body.asString.map(_.fromJson[Json.Obj].toOption.flatMap(_.get("access_token").flatMap(_.asString)).get)
    _ <- Console.printLine(s"TOKEN TTT: $token")
  yield token

