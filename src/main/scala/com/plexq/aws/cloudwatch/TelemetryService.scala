package com.plexq.aws.cloudwatch

import akka.actor.{ActorRef, ActorSystem}
import com.amazonaws.services.cloudwatch.model.{Dimension, StandardUnit}
import com.plexq.aws.cloudwatch.TelemetryActor.FlushMessages

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class TelemetryService(metricsService: MetricsService, telemetryActor: ActorRef)(implicit  system: ActorSystem, ec: ExecutionContext) extends Logging {
  def asyncTrace[T](namespace: String, metricName: String, msgF: Option[T] => String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, msgF, logger.trace(_), logger.trace(_,_))(f)
  def asyncInfo[T](namespace: String, metricName: String, msgF: Option[T] => String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, msgF, logger.info(_), logger.info(_,_))(f)
  def asyncError[T](namespace: String, metricName: String, msgF: Option[T] => String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, msgF, logger.error(_), logger.error(_,_))(f)
  def asyncDebug[T](namespace: String, metricName: String, msgF: Option[T] => String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, msgF, logger.debug(_), logger.debug(_,_))(f)
  def asyncWarn[T](namespace: String, metricName: String, msgF: Option[T] => String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, msgF, logger.warn(_), logger.warn(_,_))(f)

  def asyncTrace[T](namespace: String, metricName: String, msg: String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, _ => msg, logger.trace(_), logger.trace(_,_))(f)
  def asyncInfo[T](namespace: String, metricName: String, msg: String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, _ => msg, logger.info(_), logger.info(_,_))(f)
  def asyncError[T](namespace: String, metricName: String, msg: String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, _ => msg, logger.error(_), logger.error(_,_))(f)
  def asyncDebug[T](namespace: String, metricName: String, msg: String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, _ => msg, logger.debug(_), logger.debug(_,_))(f)
  def asyncWarn[T](namespace: String, metricName: String, msg: String)(f: => Future[T]): Future[T] = asyncLogging[T](namespace, metricName, _ => msg, logger.warn(_), logger.warn(_,_))(f)

  def withTrace[T](namespace: String, metricName: String, msg: String)(f: => T): T = withLogging[T](namespace, metricName, _ => msg, logger.trace(_), logger.trace(_,_))(f)
  def withInfo[T](namespace: String, metricName: String, msg: String)(f: => T): T = withLogging[T](namespace, metricName, _ => msg, logger.info(_), logger.info(_,_))(f)
  def withError[T](namespace: String, metricName: String, msg: String)(f: => T): T = withLogging[T](namespace, metricName, _ => msg, logger.error(_), logger.error(_,_))(f)
  def withDebug[T](namespace: String, metricName: String, msg: String)(f: => T): T = withLogging[T](namespace, metricName, _ => msg, logger.debug(_), logger.debug(_,_))(f)
  def withWarn[T](namespace: String, metricName: String, msg: String)(f: => T): T = withLogging[T](namespace, metricName, _ => msg, logger.warn(_), logger.warn(_,_))(f)

  def withTiming[T](metricName: String)(f: => T): T = {
    val start = System.currentTimeMillis()
    val result = f
    logMetric(TelemetryService.timingNamespace, TelemetryService.timingMetricName, "Timing", (System.currentTimeMillis() - start).toDouble,
      Some(new Dimension().withName("label").withValue(metricName)), Some(StandardUnit.Milliseconds))
    logMetric(TelemetryService.timingNamespace, TelemetryService.invocationsMetricName, "Invocation", (System.currentTimeMillis() - start).toDouble,
      Some(new Dimension().withName("label").withValue(metricName)), Some(StandardUnit.Milliseconds))
    result
  }

  def withAsyncTiming[T](metricName: String)(f: => Future[T]): Future[T] = {
    val start = System.currentTimeMillis()
    f.andThen { case r =>
      logMetric(TelemetryService.timingNamespace, TelemetryService.timingMetricName, "Timing", (System.currentTimeMillis() - start).toDouble,
        Some(new Dimension().withName("milliseconds").withValue(metricName)), Some(StandardUnit.Milliseconds))
      logMetric(TelemetryService.timingNamespace, TelemetryService.invocationsMetricName, "Invocation", (System.currentTimeMillis() - start).toDouble,
        Some(new Dimension().withName("count").withValue(metricName)), Some(StandardUnit.Count))
      println("Timing %s %d".format(metricName, System.currentTimeMillis() - start))
      r
    }
  }

  def logMetric(namespace: String, metricName: String, msg: String, value: Double, dimension: Option[Dimension] = None, unit: Option[StandardUnit] = None) = {
    telemetryActor.tell(TelemetryValueDataWithDimension(namespace, metricName, msg, value, dimension, unit), null)
  }

  system.scheduler.scheduleAtFixedRate(500.milliseconds, 500.milliseconds, telemetryActor, FlushMessages)

  private def withLogging[T](namespace: String, metricName: String, msg: Option[T] => String, ll: => String => Unit, lle: => (String, Throwable) => Unit)(f: => T): T = {
    val start = System.currentTimeMillis()
    try {
      telemetryActor.tell(TelemetryIncrementalData(namespace, metricName, "Invocations"), null)
      //metricsService.logMetric(namespace + "/" + metricName, "Invocations", 1)
      val r = f
      //val trace = new Throwable().getStackTrace()
      ll("%s/%s:%dms- %s".format(namespace, metricName, System.currentTimeMillis() - start, msg(Some(r))))
      r
    }
    catch {
      case NonFatal(e) => {
        lle("Failed executing %s/%s:%sms - %s".format(namespace, metricName, System.currentTimeMillis() - start, msg(None)), e)
        //metricsService.logMetric(namespace + "/" + metricName, "Errors", 1)
        telemetryActor.tell(TelemetryIncrementalData(namespace, metricName, "Errors"), null)
        throw(e)
      }
    }
  }

  private def asyncLogging[T](namespace: String, metricName: String, msg: Option[T] => String, ll: => String => Unit, lle: => (String, Throwable) => Unit)(f: => Future[T]): Future[T] = {
    val start = System.currentTimeMillis()

    telemetryActor.tell(TelemetryIncrementalData(namespace, metricName, "Invocations"), null)
    println(s"Invoking ${namespace}/${metricName} - ${msg(None)}")
    //metricsService.logMetric(namespace + "/" + metricName,"Invocations", 1)
    val r = f.map { x =>
      //val trace = new Throwable().getStackTrace()
      ll("%s/%s:%dms - %s".format(namespace, metricName, System.currentTimeMillis() - start, msg(Some(x))))
      x
    } recoverWith {
      case NonFatal(e) => {
        lle("Failed executing %s/%s:%sms - %s".format(namespace, metricName, System.currentTimeMillis() - start, msg(None)), e)
        //metricsService.logMetric(namespace + "/" + metricName, "Errors", 1)
        telemetryActor.tell(TelemetryIncrementalData(namespace, metricName, "Errors"), null)

        Future.failed(e)
      }
    }
    r
  }
}

object TelemetryService {
  val timingNamespace = "System::Timing"
  val timingMetricName = "ExecutionTime"
  val invocationsMetricName = "Invocations"

  val dataErrors ="System::DataErrors"
  val systemExceptions = "System:Exception"

  object DataErrors {
    val serializationError = "SerializationError"
    val inconsistentData = "InconsistentData"
  }

  object SystemExceptions {
    val unexpectedException = "UnexpectedException"
  }
}