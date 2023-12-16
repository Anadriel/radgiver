package com.github.fermorg.radgiver.model.http

import zio.json.{DeriveJsonCodec, JsonCodec}

case class PromptedPrediction(prediction: String)

object PromptedPrediction:
  given JsonCodec[PromptedPrediction] = DeriveJsonCodec.gen[PromptedPrediction]
