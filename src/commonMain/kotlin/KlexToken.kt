val keywords = listOf("function", "return", "if", "else")

sealed class KlexToken(open val colorHex: String? = null)

sealed class ValToken(colorHex: String? = null) : KlexToken(colorHex)

data object WhiteSpace : KlexToken() // We don't need to remember the content, as that's still in the parsed tree.
data class Identifier(val name: String) : ValToken() {
    override val colorHex: String
        get() = if (name in keywords) "#F86700" else "#DDD"
}

// Operators
sealed class Operator : KlexToken() { // Base class for operators, easy parsing
    abstract fun apply(left: Any?, right: Any?): Any?
}
// math
data object OperatorPlus : Operator() { // +
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is String || right is String) return left.toString() + right.toString()
        if (left is Number && right is Number) return left.toDouble() + right.toDouble()

        return null
    }
}

data object OperatorMinus : Operator() { // -
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() - right.toDouble()

        return null
    }
}

data object OperatorMultiply : Operator() { // *
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() * right.toDouble()

        return null
    }
}

data object OperatorDivide : Operator() { // /
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() / right.toDouble()

        return null
    }
}

fun Any?.jsBool(): Boolean = !!this.asDynamic() as Boolean

// comparisons
data object OperatorEquals : Operator() { // ==
    override fun apply(left: Any?, right: Any?): Any? {
        return left == right
    }
}

data object OperatorLess : Operator() { // <
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() < right.toDouble()

        return null
    }
}

data object OperatorGreater : Operator() { // >
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() > right.toDouble()

        return null
    }
}
data object OperatorLessEquals : Operator() { // <=
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() <= right.toDouble()

        return null
    }
}
data object OperatorGreaterEquals : Operator() { // >=
    override fun apply(left: Any?, right: Any?): Any? {
        if (left is Number && right is Number) return left.toDouble() >= right.toDouble()

        return null
    }
}
// boolean ops
data object OperatorOr : Operator() { // ||
    override fun apply(left: Any?, right: Any?): Any? {
        return left.jsBool() || right.jsBool()
    }
}

data object OperatorAnd : Operator() { // &&
    override fun apply(left: Any?, right: Any?): Any? {
        return left.jsBool() && right.jsBool()
    }
}

// Assignment
data object Assign : KlexToken() // =

// Data types
sealed class ValLiteralToken(open val content: Any?, colorHex: String? = null) : ValToken(colorHex)
data class StringToken(override val content: String) : ValLiteralToken(content, "#00FF00") // "Example"
data class NumberToken(override val content: Number) : ValLiteralToken(content) // 1.4e+5
data class BoolToken(override val content: Boolean) : ValLiteralToken(content, "#FE9900") // true
data object NullToken : ValLiteralToken(null, "#FE9900") // true

// Syntax
data object OpenParentheses : KlexToken() // (
data object CloseParentheses : KlexToken() // )
data object OpenCurly : KlexToken() // {
data object CloseCurly : KlexToken() // }
data object CommaSep : KlexToken() // ,

data object Negate : KlexToken() // !

data class Comment(val closed: Boolean) : KlexToken("#AAA") // //

data object Unknown : KlexToken("#F00")
