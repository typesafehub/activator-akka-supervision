package supervision

// Represents an arithmetic expression involving integer numbers
trait Expression {
  def left: Expression
  def right: Expression
}

// Basic arithmetic operations that are supported by the ArithmeticService. Every
// operation except the constant value has a left and right side. For example
// the addition in (3 * 2) + (6 * 6) has the left side (3 * 2) and the right
// side (6 * 6).
case class Add(left: Expression, right: Expression) extends Expression {
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
