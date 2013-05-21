/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.activator.supervision

import akka.actor.{ActorSystem, Props, OneForOneStrategy, Actor}
import akka.actor.SupervisorStrategy.{Restart, Stop, Escalate}
import scala.collection.immutable._
import scala.concurrent.forkjoin.ThreadLocalRandom
import akka.event.Logging
import scala.util.control.NonFatal

// Represents an arithmetic expression involving integer numbers
trait Expression {
  def left: Expression
  def right: Expression
}

// Basic arithmetic operations that are supported by the ArithmeticService. Every operation except the constant value
// has a left and right side. For example the addition in (3 * 2) + (6 * 6) has the left side (3 * 2) and the right
// side (6 * 6).
case class Add(left: Expression, right: Expression) extends Expression{
  override val toString = s"($left + $right)"
}
case class Multiply(left: Expression, right: Expression) extends Expression {
  override val toString = s"($left * $right)"
}
case class Divide(left: Expression, right: Expression) extends Expression {
  override val toString = s"($left / $right)"
}
case class Const(value: Int) extends Expression {
  def left = this
  def right = this
  override val toString = String.valueOf(value)
}

// A very simple service that accepts arithmetic expressions and tries to evaluate them. Since the calculation is
// dangerous (at least for the sake of this example) it is delegated to a worker actor of type FlakyExpressionCalculator.
class ArithmeticService extends Actor {
  val log = Logging(context.system, this)
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
      log.error("Evaluation failed beause of: {}", e.getMessage)
      Stop
    // It is a good practice to use scala.util.control.NonFatal to not match on dangerous exceptions. See the
    // ScalaDocs for details on what this does.
    case NonFatal(e) =>
      log.error(e, "Unexpected failure.")
      Stop
  }

  def receive = {
    case e: Expression =>
      // We delegate the dangerous task of calculation to a worker, passing the expression as a constructor argument
      // to the actor.
      context.actorOf(FlakyExpressionCalculator(e, Left))
    case Result(expr, value, _) => log.info("Result {} = {}", expr, value)
  }

}

trait Position
case object Left extends Position
case object Right extends Position
case class Result(expr: Expression, value: Int, position: Position)

class FlakynessException extends Exception("Flakyness")

object FlakyExpressionCalculator {
  def apply(expr: Expression, position: Position): Props = Props(classOf[FlakyExpressionCalculator], expr, position)
}

// This actor has the sole purpose of calculating a given expression and return the result to its parent. It takes
// an additional argument, myPosition, which is used to signal the parent which side of its expression has been
// calculated.
// If the expression is a Const then this actor replies to its parent with an immediate answer. If the expression is
// an arithmetic operation, this actor will create two children workers to first evaluate both sides of the operation
// and then proceeds evaluating the original expression. For example if (3 + 4) * (11 + 1) is to be evaluated then
// (3 + 4) is passed to one child worker, and (11 + 1) is to the other. After they return 7 and 12 (in any order), the
// final value of 7 * 12 = 84 is delivered to the parent.
class FlakyExpressionCalculator(val expr: Expression, val myPosition: Position) extends Actor {
  val log = Logging(context.system, this)
  // Similar to the supervisor strategy of the ArithmeticService but when an ArithmeticException is encountered
  // it is escalated to the parent. The parent of this actor is either another FlakyExpressionCalculator or the
  // ArithmeticService. Since the calculators all escalate, no matter how deep the exception happened, the
  // ArithmeticService will decide on the fate of the job (in our case, stop it).
  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case _: FlakynessException =>
      log.warning("Evaluation of {} failed, restarting.", expr)
      Restart
    case _: ArithmeticException =>
      Escalate
  }

  // The value of these variables will be reinitialized after every restart. The only stable data the actor has
  // during restarts is those embedded in the Props when it was created. In this case expr, and myPosition.
  var results  = Map.empty[Position, Int]
  var expected = Set[Position](Left, Right)

  override def preStart(): Unit = expr match {
    case Const(value) =>
      context.parent ! Result(expr, value, myPosition)
      expected = Set.empty
    case _ =>
      context.actorOf(FlakyExpressionCalculator(expr.left, Left), name = "left")
      context.actorOf(FlakyExpressionCalculator(expr.right, Right), name = "right")
  }

  def receive = {
    case Result(_, value, position) if expected(position) =>
      expected -= position
      results += position -> value
      if (results.size == 2) {
        // Sometimes we fail to calculate
        flakyness()
        val result: Int = evaluate(expr, results(Left), results(Right))
        log.info("Evaluated expression {} to value {}", expr, result)
        context.parent ! Result(expr, result, myPosition)
      }
    case Result(_, _, position) =>
      throw new IllegalStateException(s"Expected results for positions ${expected.mkString(", ")} but got position $position")
  }

  private def evaluate(expr: Expression, left: Int, right: Int): Int = expr match {
    case Add(_, _)      => left + right
    case Multiply(_, _) => left * right
    case Divide(_, _)   => left / right
  }

  private def flakyness(): Unit =
    if (ThreadLocalRandom.current().nextBoolean())
      throw new FlakynessException

}

// DUMMY MAIN, WILL BE REMOVED
object Main {
  def main(args: Array[String]) {
    val system = ActorSystem("calculator-system")
    val calculatorService = system.actorOf(Props[ArithmeticService], "arithmetic-service")

    // (3 + 5) / (2 * (1 + 1))
    val task = Divide(
      Add(Const(3), Const(5)),
      Multiply(
        Const(2),
        Add(Const(1), Const(1))
      )
    )

    calculatorService ! Divide(Const(3), Const(0))
    calculatorService ! task

    Thread.sleep(1000)
    system.shutdown()
  }
}