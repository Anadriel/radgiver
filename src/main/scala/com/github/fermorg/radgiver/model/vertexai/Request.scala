package com.github.fermorg.radgiver.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Request(
  instances: List[Instance],
  parameters: Parameters,
)

object Request:
  given JsonCodec[Request] = DeriveJsonCodec.gen[Request]
