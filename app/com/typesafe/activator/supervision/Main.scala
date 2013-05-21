package com.typesafe.activator.supervision

import akka.actor.{Props, ActorSystem}

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

    // This will fail with ArithmeticException -- the job will fail, but the service will remain intact
    calculatorService ! Divide(Const(3), Const(0))
    // This will with NullPointerException -- the job will fail, but the service will remain intact
    calculatorService ! Add(null, null)
    calculatorService ! task

    // Calculation happens asynchronously -- we give a small time for the system to finish before we shut it down
    Thread.sleep(1000)

    system.shutdown()
    system.awaitTermination()
  }
}
