package com.github.fermorg.radgiver.config

import zio.{Config, Duration}
import zio.config.magnolia.deriveConfig

import scala.jdk.DurationConverters.*

case class QuotaConfig(count: Int, duration: Duration)

object QuotaConfig {

  implicit val config: Config[QuotaConfig] =
    deriveConfig[QuotaConfig].validate("Invalid Quota config") { qc =>
      qc.count > 0 && !qc.duration.isNegative && !qc.duration.isZero && qc.duration.toScala.isFinite
    }

}
