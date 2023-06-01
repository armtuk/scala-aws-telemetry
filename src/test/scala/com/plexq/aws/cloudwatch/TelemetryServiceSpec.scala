package com.plexq.aws.cloudwatch

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, MustMatchers, OptionValues, WordSpec, WordSpecLike}


class TelemetryServiceSpec extends TelemetrySpec with BeforeAndAfterAll {
  val actor = system.actorOf(Props.apply(classOf[TelemetryActor], metricsService, system))
  val service = new TelemetryService(metricsService, telemetryActor = actor)(system, ec)


  "TelemetryService" should {
    "log a metric" in {
      service.logMetric(TestConstants.namespace, "test", "Test Logging a metric", 1.0)

      ensureMetricsCount(1) must be (true)
    }

    "log around a body" in {
      service.withInfo(TestConstants.namespace, "test", "Test info logger") {
        System.out.println("Test")
      }

      ensureMetricsCount(1) must be (true)
    }
  }

  override protected def beforeAll(): Unit = super.beforeAll()
}
