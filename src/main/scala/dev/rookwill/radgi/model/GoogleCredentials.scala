package dev.rookwill.radgi.model

import zio.json.{DeriveJsonDecoder, JsonDecoder}

case class GoogleCredentials(
  private_key_id: String,
  private_key: String,
  client_email: String
)

object GoogleCredentials:
  given JsonDecoder[GoogleCredentials] = DeriveJsonDecoder.gen[GoogleCredentials]