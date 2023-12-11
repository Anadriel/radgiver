package dev.rookwill.radgi.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Parameters(
    temperature: Double,
    maxOutputTokens: Int,
    topP: Double,
    topK: Int
)

object Parameters:
  given JsonCodec[Parameters] =
    DeriveJsonCodec.gen[Parameters]
