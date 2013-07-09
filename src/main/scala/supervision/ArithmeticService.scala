package supervision

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{ActorRef, ActorLogging, Actor, OneForOneStrategy, Status}

// A very simple service that accepts arithmetic expressions and tries to
// evaluate them. Since the calculation is dangerous (at least for the sake
// of this example) it is delegated to a worker actor of type
// FlakyExpressionCalculator.
class ArithmeticService extends Actor with ActorLogging {
  import FlakyExpressionCalculator.{FlakinessException, Result, Left}

  // Map of workers to the original actors requesting the calculation
  var pendingWorkers = Map[ActorRef, ActorRef]()

  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = false) {
    case _: FlakinessException =>
      log.warning("Evaluation of a top level expression failed, restarting.")
      Restart
    case e: ArithmeticException =>
      log.error("Evaluation failed because of: {}", e.getMessage)
      notifyConsumerFailure(worker = sender, failure = e)
      Stop
    case e =>
      log.error("Unexpected failure: {}", e.getMessage)
      notifyConsumerFailure(worker = sender, failure = e)
      Stop
  }

  def notifyConsumerFailure(worker: ActorRef, failure: Throwable): Unit = {
    // Status.Failure is a message type provided by the Akka library. The
    // reason why it is used because it is recognized by the "ask" pattern
    // and the Future returned by ask will fail with the provided exception.
    pendingWorkers.get(worker) foreach { _ ! Status.Failure(failure) }
    pendingWorkers -= worker
  }

  def notifyConsumerSuccess(worker: ActorRef, result: Int): Unit = {
    pendingWorkers.get(worker) foreach { _ ! result }
    pendingWorkers -= worker
  }

  def receive = {
    case e: Expression =>
      // We delegate the dangerous task of calculation to a worker, passing the
      // expression as a constructor argument to the actor.
      val worker = context.actorOf(FlakyExpressionCalculator.props(
        expr = e,
        position = Left)
      )
      pendingWorkers += worker -> sender

    case Result(originalExpression, value, _) =>
      notifyConsumerSuccess(worker = sender, result = value)
  }

}