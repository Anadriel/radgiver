package com.github.fermorg.radgiver.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Instance(
  context: String,
  examples: List[Example],
  messages: List[Message],
)

object Instance:
  given JsonCodec[Instance] = DeriveJsonCodec.gen[Instance]
