package com.github.fermorg.radgiver.model.http

import zio.json.{DeriveJsonCodec, JsonCodec}

case class BlobContent(content: String)

object BlobContent:
  given JsonCodec[BlobContent] = DeriveJsonCodec.gen[BlobContent]
