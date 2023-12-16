package com.github.fermorg.radgiver.model.deichman

import zio.json.*

import java.time.ZonedDateTime

case class EventRef(
  id: String,
  title: String,
  library: String,
  cancelled: Boolean,
  startTime: ZonedDateTime,
  endTime: ZonedDateTime,
)

object EventRef:

  given JsonCodec[EventRef] = DeriveJsonCodec.gen[EventRef]
