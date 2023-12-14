package com.github.fermorg.radgiver.model.deichman

import zio.json.{DeriveJsonCodec, JsonCodec}

case class EventInfo(
  title: String,
  description: String,
)

object EventInfo:
  given JsonCodec[EventInfo] = DeriveJsonCodec.gen[EventInfo]