package com.github.fermorg.radgiver.model.http

import zio.json.{DeriveJsonCodec, JsonCodec}

case class WriteBlob(
  path: String,
  content: String,
  overwrite: Boolean,
)

object WriteBlob:
  given JsonCodec[WriteBlob] = DeriveJsonCodec.gen[WriteBlob]
