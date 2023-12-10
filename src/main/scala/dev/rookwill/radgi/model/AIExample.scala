package dev.rookwill.radgi.model

import zio.json.*
case class AIExample(input: AIMessage, output: AIMessage)

object AIExample:
  given JsonEncoder[AIExample] = DeriveJsonEncoder.gen[AIExample]
  given JsonDecoder[AIExample] = DeriveJsonDecoder.gen[AIExample]