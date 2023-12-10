package dev.rookwill.radgi.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AIPrediction(candidates: List[AICandidate])

object AIPrediction:
  given JsonEncoder[AIPrediction] = DeriveJsonEncoder.gen[AIPrediction]

  given JsonDecoder[AIPrediction] = DeriveJsonDecoder.gen[AIPrediction]