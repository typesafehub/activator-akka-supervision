/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.activator.supervision

import akka.actor.{ActorSystem, Props, OneForOneStrategy, Actor}
import akka.actor.SupervisorStrategy.{Restart, Stop, Escalate}
import scala.collection.immutable._
import scala.concurrent.forkjoin.ThreadLocalRandom

trait Expression {
  def left: Expression
  def right: Expression
}

case class Add(left: Expression, right: Expression) extends Expression
case class Multiply(left: Expression, right: Expression) extends Expression
case class Divide(left: Expression, right: Expression) extends Expression
case class Const(value: Int) extends Expression {
  def left = this
  def right = this
}


class ArithmeticService extends Actor {
  override val supervisorStrategy = OneForOneStrategy() {
    case _: FlakynessException => Restart
    case _ => Stop
  }

  def receive = {
    case e: Expression =>
      context.actorOf(FlakyExpressionCalculator(e, Left))
    case Result(value, _) => println("Got result: " + value)
  }

}

trait Position
case object Left extends Position
case object Right extends Position
case class Result(value: Int, position: Position)

object FlakyExpressionCalculator {
  def apply(expr: Expression, position: Position): Props = Props(classOf[FlakyExpressionCalculator], expr, position)
}

class FlakynessException extends Exception("Flakyness")

class FlakyExpressionCalculator(val expr: Expression, val myPosition: Position) extends Actor {
  override val supervisorStrategy = OneForOneStrategy() {
    case _: FlakynessException => Restart
    case _: ArithmeticException => Escalate
  }

  var results  = Map.empty[Position, Int]
  var expected = Set[Position](Left, Right)

  override def preStart(): Unit = expr match {
    case Const(value) =>
      context.parent ! Result(value, myPosition)
      expected = Set.empty
    case _ =>
      context.actorOf(FlakyExpressionCalculator(expr.left, Left), name = "left")
      context.actorOf(FlakyExpressionCalculator(expr.right, Right), name = "right")
  }

  def receive = {
    case Result(value, position) if expected(position) =>
      flakyness()
      expected -= position
      results += position -> value
      if (results.size == 2)
        context.parent ! Result(evaluate(expr, results(Left), results(Right)), myPosition)
    case Result(_, position) =>
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