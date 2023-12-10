package dev.rookwill.radgi.gcp

import dev.rookwill.radgi.model.{AIRequest, GoogleCredentials}
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtHeader, JwtZIOJson}
import zio.{ZIO, http}
import zio.http.Body
import zio.stream.{ZPipeline, ZSink, ZStream}
import zio.json.*

import java.time.Instant

object JWTTokenGenerator:

  private def extractCredentials(fileName: String): ZIO[Any, Exception, GoogleCredentials] =
    for
      fileContent <- ZStream.fromResource(fileName)
        .via(ZPipeline.utf8Decode).run(ZSink.foldLeft[String, String]("")(_ + _))
      creds <- ZIO.fromEither(fileContent.fromJson[GoogleCredentials]).mapError(Exception(_))
    yield creds

  private val credentials = extractCredentials("google-auth.json")
  private val algo = JwtAlgorithm.RS256

  private def composeClaim(validSecs: Long): ZIO[Any, Exception, JwtClaim] =
    for
      creds <- credentials
      justNow = Instant.now
      jwtClaim = JwtClaim(
        issuer = Some(creds.client_email),
        subject = Some(creds.client_email),
        audience = Some(Set("https://www.googleapis.com/oauth2/v4/token")),
        expiration = Some(justNow.plusSeconds(validSecs).getEpochSecond),
        issuedAt = Some(justNow.getEpochSecond)
      ) + ("scope", "https://www.googleapis.com/auth/cloud-platform")
    yield jwtClaim

  def jwtToken(validSecs: Long): ZIO[Any, Exception, String] =
    for
      claim <- composeClaim(validSecs)
      keyId <- credentials.map(_.private_key_id)
      key <- credentials.map(_.private_key)
      header = JwtHeader(algo).withKeyId(keyId)
    yield JwtZIOJson.encode(header, claim, key)


