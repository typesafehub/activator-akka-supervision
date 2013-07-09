package supervision

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.util.Timeout

object Main {

  def main(args: Array[String]) {
    val system = ActorSystem("calculator-system")
    val calculatorService =
      system.actorOf(Props[ArithmeticService], "arithmetic-service")

    def calculate(expr: Expression): Future[Int] = {
      implicit val timeout = Timeout(1.second)
      (calculatorService ? expr).mapTo[Int]
    }

    // (3 + 5) / (2 * (1 + 1))
    val task = Divide(
      Add(Const(3), Const(5)),
      Multiply(
        Const(2),
        Add(Const(1), Const(1))
      )
    )

    val result = Await.result(calculate(task), 1.second)
    println(s"Got result: $result")

    system.shutdown()
    system.awaitTermination()
  }
}
