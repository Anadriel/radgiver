package dev.rookwill.radgi.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Example(input: Message, output: Message)

object Example:
  given JsonCodec[Example] = DeriveJsonCodec.gen[Example]
