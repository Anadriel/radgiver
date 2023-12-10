package dev.rookwill.radgi.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AIResponse(predictions: List[AIPrediction])

object AIResponse:
  given JsonEncoder[AIResponse] = DeriveJsonEncoder.gen[AIResponse]

  given JsonDecoder[AIResponse] = DeriveJsonDecoder.gen[AIResponse]
