package dev.rookwill.radgi.model

import zio.json.*

case class AIMessage(author: String, content: String)
object AIMessage:
  given JsonEncoder[AIMessage] = DeriveJsonEncoder.gen[AIMessage]
  given JsonDecoder[AIMessage] = DeriveJsonDecoder.gen[AIMessage]
