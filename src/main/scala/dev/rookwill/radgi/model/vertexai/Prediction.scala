package dev.rookwill.radgi.model.vertexai

import zio.json.{DeriveJsonCodec, JsonCodec}

case class Prediction(candidates: List[Candidate])

object Prediction:

  given JsonCodec[Prediction] =
    DeriveJsonCodec.gen[Prediction]
