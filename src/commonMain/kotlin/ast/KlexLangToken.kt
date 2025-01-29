package ast

import Operator

sealed class KlexLangToken // Similar to KlexToken, except all KlexLangTokens have functionality, unlike KlexToken

data class CodeScope(val statements: List<Statement>): Statement() // Also implicit when just one statement

sealed class Statement : KlexLangToken()
data class Return(val value: Expression): Statement()
data class Assignment(val target: String, val value: Expression) : Statement() // Identifier = Expression

sealed class Expression : Statement()
// Types of expressions
data class Function(val params: List<String>, val code: Statement) : Expression() // function(params) code
data class Invoke(val function: Expression, val params: List<Expression>): Expression() // identifier(params)

data class If(val cond: Expression, val scope: Statement, val elseScope: Statement?): Statement() // Includes else, else if is implicit like any C-like language
data class OperatorExpr(val left: Expression, val right: Expression, val operator: Operator): Expression()

data class NegateExpr(val value: Expression): Expression() // Negate is the only single value operator in this language, therefore we use it like this.

sealed class ValueExpr: Expression() // Either a literal or an identifier

data class LiteralExpr(val value: Any?): ValueExpr()
data class IdentifierExpr(val identifier: String): ValueExpr()
data class GroupExpr(val expression: Expression): Expression() // (expression) // A container that ensures the tree stays steady
