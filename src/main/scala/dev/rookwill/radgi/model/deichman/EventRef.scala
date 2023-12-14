package dev.rookwill.radgi.model.deichman

import zio.json.{DeriveJsonCodec, DeriveJsonEncoder, JsonCodec, JsonEncoder}

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

  given JsonCodec[EventRef] =
    DeriveJsonCodec.gen[EventRef]
