package execution

import ast.*
import jsBool

sealed class CallableFunction
sealed class NativeFunction(val onInvoke: KlexExecutionContext.(List<Any?>) -> Result<Any?>): CallableFunction()
class NativeFunctionVoid(onInvoke: KlexExecutionContext.(List<Any?>) -> Unit): NativeFunction({ onInvoke(it);Result.success(KlexExecutionContext.NoReturn) })
class StatementFunction(val scope: KlexExecutionContext, val varNames: List<String>, val code: Statement): CallableFunction()

open class KlexVariableLookupTable {
    val mapWithAll = hashMapOf<String, Any?>()
    fun get(identifier: String): Result<Any?> {
        if (identifier !in mapWithAll.keys) return Result.failure(NoSuchElementException())
        return Result.success(mapWithAll[identifier])
    }
    fun set(identifier: String, value: Any?) {
        mapWithAll[identifier] = value
    }

    open fun getFallback(identifier: String) = get(identifier)
    open fun setFallback(identifier: String, value: Any?) = set(identifier, value)
}

/**
 * A context in which Klex executes code, includes a reference to the parent for variable fallbacks
 * @param parent The parent this is ran from, KlexExecutionContext for nested blocks, KlexVariableLookupTable for the top level.
 * @param functionScope Marks this scope as a function, so when [parentFunctionScope] is accessed, the first entry with it is returned.
 */
class KlexExecutionContext(val parent: KlexVariableLookupTable, val functionScope: Boolean = false): KlexVariableLookupTable() {
    /**
     * Get the closest function scope upwards.
     */
    val parentFunctionScope: KlexExecutionContext? get() {
        if (parent is KlexExecutionContext) {
            if (parent.functionScope) return parent
            else return parent.parentFunctionScope
        }
        else return null
    }

    val rootLookupTable: KlexVariableLookupTable get() = if (parent is KlexExecutionContext) parent.rootLookupTable else parent

    fun getAllCurrentContextVariables(): List<String> {
        return mapWithAll.keys.toMutableList().also { it.addAll(if (parent is KlexExecutionContext) parent.getAllCurrentContextVariables() else parent.mapWithAll.keys) }
    }

    override fun getFallback(identifier: String): Result<Any?> {
        return Result.success(super.get(identifier).getOrElse { return parent.getFallback(identifier) })
    }

    override fun setFallback(identifier: String, value: Any?) {
        // Find first parent which defined this variable
        var parent: KlexVariableLookupTable? = this
        while (parent != null) {
            if (parent.get(identifier).isSuccess) { // Found a predefined variable
                break
            }
            parent = if (parent is KlexExecutionContext) parent.parent else null
        }
        val target = parent ?: this // If it wasn't previously defined, define in current context

        target.set(identifier, value)
    }

    data object NoReturn // Used to indicate no return

    fun execute(statement: Statement): Result<Any?> { // Bool indicates whether to break out of parentFunctionScope or not
        when (statement) {
            is Assignment -> {
                setFallback(statement.target, executeExpression(statement.value).getOrElse { return Result.failure(it) })
                return Result.success(NoReturn) // Continue execution
            }
            is Expression -> executeExpression(statement).getOrElse { return Result.failure(it) }.let { return Result.success(NoReturn) }
            is CodeScope -> {
                KlexExecutionContext(this).let { statement.statements.forEach {s -> it.execute(s).getOrElse { return Result.failure(it) }.also { if (it !is NoReturn) return Result.success(it) } } }
                return Result.success(NoReturn)
            } // Execute all statements in a new code scope. If an error occurs, cancel execution.
            is If -> {
                if ((executeExpression(statement.cond).getOrElse { return Result.failure(it) } ?: false) != false)
                    execute(statement.scope).getOrElse { return Result.failure(it) }
                else statement.elseScope?.let { execute(it).getOrElse { return Result.failure(it) } }
                return Result.success(NoReturn) // Continue execution
            }
            is Return -> {
                return Result.success(executeExpression(statement.value).getOrElse { return Result.failure(it) }) // Stop execution of all scopes until parent
            }
        }
    }

    fun executeExpression(expr: Expression): Result<Any?> {
        return when (expr) {
            is GroupExpr -> Result.success(executeExpression(expr.expression).getOrElse { return Result.failure(it) })
            is Function -> Result.success(StatementFunction(this, expr.params, expr.code))
            is Invoke -> {
                val target = executeExpression(expr.function).getOrElse { return Result.failure(it) } // Get the function
                if (target is CallableFunction) {
                    val params = expr.params.map { executeExpression(it).getOrElse { return Result.failure(it) } }
                    if (target is NativeFunction) {
                        return target.onInvoke(this, params)
                    }
                    if (target is StatementFunction) {
                        if (params.size > target.varNames.size) return Result.failure(RuntimeException("Attempted to invoke target function but too many variables were given."))
                        val result = KlexExecutionContext(target.scope, true).also {
                            params.forEachIndexed {i, it2 ->
                                it.set(target.varNames[i], it2)
                            }
                        }.execute(target.code).getOrElse { return Result.failure(it) }
                        return Result.success(if (result is NoReturn) null else result)
                    }
                }
                Result.failure(RuntimeException("Value $target is not a function."))
            }
            is NegateExpr -> Result.success(!executeExpression(expr.value).getOrElse { return Result.failure(it) }.jsBool())
            is OperatorExpr -> Result.success(expr.operator.apply(
                executeExpression(expr.left).getOrElse { return Result.failure(it) },
                executeExpression(expr.right).getOrElse { return Result.failure(it) }
            ) ?: return Result.failure(RuntimeException("Operator $expr not implemented for types.")))
            is IdentifierExpr -> getFallback(expr.identifier)
            is LiteralExpr -> Result.success(expr.value)
        }
    }
}