package dev.rookwill.radgi.model

import zio.json.*

case class AIRequest(instances: List[AIInstance], parameters: AIParameters)

object AIRequest:
  given JsonEncoder[AIRequest] = DeriveJsonEncoder.gen[AIRequest]
  given JsonDecoder[AIRequest] = DeriveJsonDecoder.gen[AIRequest]


