package com.plexq.aws.cloudwatch

object TestConstants {
  val serviceName: String = "scala-aws-telemetry"
  val envName: String = Option(System.getenv("env")).getOrElse("local")
  val namespace = serviceName + "/" + envName
}
