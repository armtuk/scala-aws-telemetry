package com.plexq.aws.cloudwatch

import akka.actor.{Actor, ActorSystem, Timers}
import com.amazonaws.services.cloudwatch.model.{Dimension, StandardUnit}
import com.plexq.aws.cloudwatch.TelemetryActor.FlushMessages

import scala.concurrent.duration._


class TelemetryActor(metricsService: MetricsService, system: ActorSystem) extends Actor with Timers {
  var queue = List[(Long, String, TelemetryData)]()
  val retentionMillis = 100L
  val metricsGroupSize = 20

  timers.startTimerAtFixedRate("flusher", FlushMessages, 0.milliseconds, 500.milliseconds)

  def enqueue(x: TelemetryData) = {
    queue = (System.currentTimeMillis(), x.namespace, x) :: queue
    if (queue.map(_._1).min < (System.currentTimeMillis() - retentionMillis)) {
      dequeue()
    }
  }

  override def receive: Receive = {
    case x: TelemetryValueData => enqueue(x)
    case x: TelemetryIncrementalData => enqueue(x)
    case x: TelemetryValueDataWithDimension => enqueue(x)

    case FlushMessages => {
      dequeue()
    }
  }

  def dequeue() = {
    val toSend = queue
    queue = List()

    if (toSend.nonEmpty)
      toSend.groupBy(_._2).foreach { x =>
        x._2.grouped(metricsGroupSize).foreach { y =>
          metricsService.logMetrics(x._1, y.map(_._3.toGroupedMetric))
        }
      }
  }
}

object TelemetryActor {
  case object FlushMessages
}

trait TelemetryData {
  val namespace: String
  val metric: String
  val msg: String
  val value: Double
  val dimension: Option[Dimension]
  val unit: Option[StandardUnit]

  def toGroupedMetric: GroupedMetric = GroupedMetric(metric, value, dimension, unit)
}

case class TelemetryValueDataWithDimension(namespace: String, metric: String, msg: String, value: Double, dimension: Option[Dimension], unit: Option[StandardUnit]) extends TelemetryData {
}

case class TelemetryValueData(namespace: String, metric: String, msg: String, value: Double) extends TelemetryData {
  val dimension = None
  val unit = None
}

case class TelemetryIncrementalData(namespace: String, metric: String, msg: String) extends TelemetryData {
  val value = 1d
  val dimension = None
  val unit = None
}

