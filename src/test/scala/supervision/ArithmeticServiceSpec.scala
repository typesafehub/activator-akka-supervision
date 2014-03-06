/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package supervision

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Status, Props, ActorSystem}
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers}

class ArithmeticServiceSpec(_system: ActorSystem)
  extends TestKit(_system)
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  def this() = this(ActorSystem("ArithmeticServiceSpec"))

  override def afterAll: Unit = system.shutdown()

  val arithmeticService = system.actorOf(Props[ArithmeticService],
    "arithmetic-service")

  "The ArithmeticService" should "calculate constant expressions properly" in {
    for (x <- -2 to 2) {
      arithmeticService ! Const(x)
      expectMsg(x)
    }
  }

  it should "calculate addition and subtraction properly" in {
    for (x <- -2 to 2; y <- -2 to 2) {
      arithmeticService ! Add(Const(x), Const(y))
      expectMsg(x + y)
    }
  }

  it should "calculate multiplication and division properly" in {
    for (x <- -2 to 2; y <- -2 to 2) {
      arithmeticService ! Multiply(Const(x), Const(y))
      expectMsg(x * y)
    }

    // Skip zero in the second parameter
    for (x <- -2 to 2; y <- List(-2, -1, 1, 2)) {
      arithmeticService ! Divide(Const(x), Const(y))
      expectMsg(x / y)
    }
  }

  it should "survive illegal expressions" in {
    arithmeticService ! Divide(Const(1), Const(0))
    expectMsgType[Status.Failure]

    arithmeticService ! Add(null, Const(0))
    expectMsgType[Status.Failure]

    arithmeticService ! Add(Const(1), Const(0))
    expectMsg(1)
  }

}
