package supervision

import akka.actor.{ActorLogging, Actor, OneForOneStrategy}
import akka.actor.SupervisorStrategy.{Restart, Stop}
import scala.util.control.NonFatal

// A very simple service that accepts arithmetic expressions and tries to
// evaluate them. Since the calculation is dangerous (at least for the sake
// of this example) it is delegated to a worker actor of type
// FlakyExpressionCalculator.
class ArithmeticService extends Actor with ActorLogging {
  import FlakyExpressionCalculator.{FlakinessException, Result, Left}

  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case _: FlakinessException =>
      log.warning("Evaluation of a top level expression failed, restarting.")
      Restart
    case e: ArithmeticException =>
      log.error("Evaluation failed because of: {}", e.getMessage)
      Stop
    // It is a good practice to use scala.util.control.NonFatal to not catch
    // dangerous exceptions. See the ScalaDocs for details on what this does.
    case NonFatal(e) =>
      log.error("Unexpected failure: {}", e.getMessage)
      Stop
  }

  def receive = {
    case e: Expression =>
      // We delegate the dangerous task of calculation to a worker, passing the
      // expression as a constructor argument to the actor.
      context.actorOf(FlakyExpressionCalculator.props(e, Left))
    case Result(evaluatedExpression, value, _) =>
      log.info("Result {} = {}", evaluatedExpression, value)
  }

}