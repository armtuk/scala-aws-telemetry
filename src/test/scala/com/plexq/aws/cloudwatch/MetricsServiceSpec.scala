package com.plexq.aws.cloudwatch

import org.scalatest.{MustMatchers, OptionValues, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class MetricsServiceSpec extends TelemetrySpec {

  "Metrics Service" should {
    "log a metric using a grouped metric" in {
      metricsService.logMetrics("test", Seq(GroupedMetric("testExecution")))
      ensureMetricsCount(1) must be (true)
    }

    "log a metric using direct call" in {
      metricsService.logMetric("test", "testExecution")
      ensureMetricsCount(1) must be (true)
    }
  }
}
