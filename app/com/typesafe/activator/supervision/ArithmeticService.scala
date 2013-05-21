package com.typesafe.activator.supervision

import akka.actor.{ActorLogging, OneForOneStrategy, Actor}
import akka.actor.SupervisorStrategy.{Restart, Stop}
import scala.util.control.NonFatal

// A very simple service that accepts arithmetic expressions and tries to evaluate them. Since the calculation is
// dangerous (at least for the sake of this example) it is delegated to a worker actor of type FlakyExpressionCalculator.
class ArithmeticService extends Actor with ActorLogging {
  // We are customizing the supervisor strategy of the service here. We set loggingEnabled to false, since we want
  // to have custom logging.
  // Our strategy here is to restart the child when a recoverable error is detected (in our case the dummy
  // FlakynessException), but when arithmetic errors happen -- like division by zero -- we have no hope to recover
  // therefore we stop the worker.
  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case _: FlakynessException =>
      log.warning("Evaluation of a top level expression failed, restarting.")
      Restart
    case e: ArithmeticException =>
      log.error("Evaluation failed because of: {}", e.getMessage)
      Stop
    // It is a good practice to use scala.util.control.NonFatal to not match on dangerous exceptions. See the
    // ScalaDocs for details on what this does.
    case NonFatal(e) =>
      log.error("Unexpected failure: {}", e.getMessage)
      Stop
  }

  def receive = {
    case e: Expression =>
      // We delegate the dangerous task of calculation to a worker, passing the expression as a constructor argument
      // to the actor.
      context.actorOf(FlakyExpressionCalculator(e, Left))
    case Result(evaluatedExpression, value, _) => log.info("Result {} = {}", evaluatedExpression, value)
  }

}