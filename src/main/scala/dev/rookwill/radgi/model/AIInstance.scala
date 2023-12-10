package dev.rookwill.radgi.model

import zio.json.*
case class AIInstance(context: String, examples: List[AIExample], messages: List[AIMessage])

object AIInstance:
  given JsonEncoder[AIInstance] = DeriveJsonEncoder.gen[AIInstance]
  given JsonDecoder[AIInstance] = DeriveJsonDecoder.gen[AIInstance]
