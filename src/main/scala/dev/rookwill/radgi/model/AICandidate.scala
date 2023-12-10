package dev.rookwill.radgi.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AICandidate(content: String, author: String)

object AICandidate:
  given JsonEncoder[AICandidate] = DeriveJsonEncoder.gen[AICandidate]

  given JsonDecoder[AICandidate] = DeriveJsonDecoder.gen[AICandidate]
