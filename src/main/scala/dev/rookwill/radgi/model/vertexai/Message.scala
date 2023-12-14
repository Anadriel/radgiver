package dev.rookwill.radgi.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Message(author: String, content: String)

object Message:
  given JsonCodec[Message] = DeriveJsonCodec.gen[Message]
