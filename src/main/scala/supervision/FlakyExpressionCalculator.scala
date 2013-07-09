package supervision

import akka.actor._
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import scala.collection.immutable._
import scala.concurrent.forkjoin.ThreadLocalRandom
import FlakyExpressionCalculator._

object FlakyExpressionCalculator {
  def props(expr: Expression, position: Position): Props =
    Props(classOf[FlakyExpressionCalculator], expr, position)

  // Encodes the original position of a sub-expression in its parent expression
  // Example: (4 / 2) has position Left in the original expression (4 / 2) * 3
  trait Position
  case object Left extends Position
  case object Right extends Position
  case class Result(originalExpression: Expression, value: Int, position: Position)

  class FlakinessException extends Exception("Flakiness")
}

// This actor has the sole purpose of calculating a given expression and
// return the result to its parent. It takes an additional argument,
// myPosition, which is used to signal the parent which side of its
// expression has been calculated.
class FlakyExpressionCalculator(
  val expr: Expression,
  val myPosition: Position)
  extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case _: FlakinessException =>
      log.warning("Evaluation of {} failed, restarting.", expr)
      Restart
    case _ =>
      Escalate
  }

  // The value of these variables will be reinitialized after every restart.
  // The only stable data the actor has during restarts is those embedded in
  // the Props when it was created. In this case expr, and myPosition.
  var results  = Map.empty[Position, Int]
  var expected = Set[Position](Left, Right)

  override def preStart(): Unit = expr match {
    case Const(value) =>
      context.parent ! Result(expr, value, myPosition)
      // Don't forget to stop the actor after it has nothing more to do
      context.stop(self)
    case _ =>
      context.actorOf(FlakyExpressionCalculator.props(expr.left, Left),
        name = "left")
      context.actorOf(FlakyExpressionCalculator.props(expr.right, Right),
        name = "right")
  }

  def receive = {
    case Result(_, value, position) if expected(position) =>
      expected -= position
      results += position -> value
      if (results.size == 2) {
        // Sometimes we fail to calculate
        flakiness()
        val result: Int = evaluate(expr, results(Left), results(Right))
        log.info("Evaluated expression {} to value {}", expr, result)
        context.parent ! Result(expr, result, myPosition)
        // Don't forget to stop the actor after it has nothing more to do
        context.stop(self)
      }
    case Result(_, _, position) =>
      throw new IllegalStateException(
        s"Expected results for positions ${expected.mkString(", ")} " +
        s"but got position $position"
      )
  }

  private def evaluate(expr: Expression, left: Int, right: Int): Int = expr match {
    case _: Add      => left + right
    case _: Multiply => left * right
    case _: Divide   => left / right
  }

  private def flakiness(): Unit =
    if (ThreadLocalRandom.current().nextDouble() < 0.2)
      throw new FlakinessException

}