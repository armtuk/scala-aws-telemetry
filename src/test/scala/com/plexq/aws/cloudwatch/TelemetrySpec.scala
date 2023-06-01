package com.plexq.aws.cloudwatch

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, MustMatchers, OptionValues, WordSpecLike}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext

abstract class TelemetrySpec extends TestKit(ActorSystem("TelemetryServiceSpec")) with WordSpecLike with MustMatchers with OptionValues with BeforeAndAfterAll {
  val timeoutMillis = 5000L

  implicit val ec: ExecutionContext = global

  val metricsService = new MetricsService(TestConstants.serviceName, TestConstants.envName, ec)

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def ensureMetricsCount(i: Int): Boolean = {
    val start = System.currentTimeMillis()

    while(System.currentTimeMillis() < (start + timeoutMillis)) {
      if (metricsService.internalCounter  >= i) return true
      else Thread.sleep(100)
    }
    false
  }
}
