package dev.rookwill.radgi.model

import zio.json.*
case class AIParameters(temperature: Double, maxOutputTokens: Int, topP: Double, topK: Int)

object AIParameters:
  given JsonEncoder[AIParameters] = DeriveJsonEncoder.gen[AIParameters]
  given JsonDecoder[AIParameters] = DeriveJsonDecoder.gen[AIParameters]