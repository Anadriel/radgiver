package com.github.fermorg.radgiver.model.http

import zio.json.{DeriveJsonCodec, JsonCodec}

case class ErrorResponse(message: String, code: Int)

object ErrorResponse:
  given JsonCodec[ErrorResponse] = DeriveJsonCodec.gen[ErrorResponse]

  def fromError(error: Throwable): ErrorResponse =
    ErrorResponse(Option(error.getMessage).getOrElse("Unknown error"), 500)
