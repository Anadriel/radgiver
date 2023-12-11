package dev.rookwill.radgi.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Candidate(content: String, author: String)

object Candidate:
  given JsonCodec[Candidate] = DeriveJsonCodec.gen[Candidate]
