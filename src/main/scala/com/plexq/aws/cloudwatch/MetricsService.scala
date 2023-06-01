package com.plexq.aws.cloudwatch

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import com.plexq.aws.CredentialHelper
import com.typesafe.config.Config

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.SeqHasAsJava

@Singleton
class MetricsService(serviceName: String, envName: String, implicit val ec: ExecutionContext) {
  val awsCreds = CredentialHelper.buildCredentials
  val client = AmazonCloudWatchClientBuilder.defaultClient
  var internalCounter = 0

  def logMetric(namespace: String, metricName: String, value: Double = 1.0, dimension: Option[Dimension] = None, unit: Option[StandardUnit] = None) = {
    val datum = new MetricDatum().withMetricName(metricName).withUnit(unit.getOrElse(StandardUnit.None)).withValue(value)
      .withDimensions(dimension.toSeq:_*)
    val request = new PutMetricDataRequest().withNamespace(serviceName + "/" + envName + "/Server/"+namespace).withMetricData(datum)
    internalCounter += 1
    client.putMetricData(request)
  }

  def logMetrics(namespace: String, metrics: Seq[GroupedMetric]) = {
    val request = new PutMetricDataRequest().withNamespace(serviceName + "/" + envName + "/Server/"+namespace)

    request.withMetricData(
      metrics.map { x =>
        new MetricDatum().withMetricName(x.metricName).withUnit(x.unit.getOrElse(StandardUnit.None)).withValue(x.value)
          .withDimensions(x.dimension.toSeq: _*)
      }.asJava
    )

    internalCounter += metrics.size

    client.putMetricData(request)
  }
}

case class GroupedMetric(metricName: String, value: Double = 1.0, dimension: Option[Dimension] = None, unit: Option[StandardUnit] = None)