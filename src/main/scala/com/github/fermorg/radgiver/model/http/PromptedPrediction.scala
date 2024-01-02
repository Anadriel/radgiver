package com.github.fermorg.radgiver.model.http

import zio.json.ast.Json
import zio.json.{DeriveJsonCodec, JsonCodec}

case class PromptedPrediction(prediction: Json.Obj)

object PromptedPrediction:
  given JsonCodec[PromptedPrediction] = DeriveJsonCodec.gen[PromptedPrediction]
