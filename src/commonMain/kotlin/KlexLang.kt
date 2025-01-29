import ast.*
import com.mylosoftworks.klex.AnyCount
import com.mylosoftworks.klex.Klex
import com.mylosoftworks.klex.OneOrMore
import com.mylosoftworks.klex.Optional
import com.mylosoftworks.klex.parsing.KlexTree
import execution.KlexExecutionContext
import execution.KlexVariableLookupTable
import kotlin.math.exp

object KlexLang {
    // This object will be accessed from javascript
    val klexTokenizer = Klex.create<KlexToken> {
        AnyCount {
            oneOf({
                // Whitespace
                OneOrMore {
                    -" \r\n\t"
                }
                treeValue = WhiteSpace
            }, {
                var closed = false // Used to mark syntax errors
                oneOf({
                    +"//"
                    AnyCount {
                        -!"\n"
                    }
                    closed = true
                }, {
                    +"/*"
                    AnyCount {
                        oneOf({
                            -!"*"
                        }, {
                            +"*"
                            -!"/"
                        })
                    }
                    Optional {
                        +"*/"
                        closed = true
                    }
                })
                treeValue = Comment(closed)
            }, {
                val boolString = oneOf({+"true"}, {+"false"}).getOrElse { return@oneOf }.content
                treeValue = BoolToken(boolString == "true")
            }, {
                +"null"
                treeValue = NullToken
            }, {
                val identifier = group {
                    -"a-zA-Z"
                    AnyCount {
                        -"a-zA-Z0-9"
                    }
                }.getOrElse { return@oneOf }.content
                treeValue = Identifier(identifier)
            }, {
                val strChar = group { -"'\"`" }.getOrElse { return@oneOf }.content
                val content = group {
                    AnyCount {
                        oneOf({+"\\\\"}, {+"\\$strChar"}, {
                            -!strChar
                        })
                    }
                }.getOrElse { return@oneOf }.content
                +strChar // Close with same char only
                treeValue = StringToken(content)
            }, {
                val numString = group {
                    AnyCount {
                        -"0-9"
                    }
                    Optional {
                        +"."
                    }
                    AnyCount {
                        -"0-9"
                    }
                    Optional {
                        -"eE" // Case-insensitive
                        AnyCount {
                            -"0-9"
                        }
                    }
                }.getOrElse { return@oneOf }.content

                val parsed = numString.toLongOrNull() ?: numString.toDoubleOrNull()
                if (parsed == null) return@oneOf fail("Unparsable number")

                treeValue = NumberToken(parsed)
            }, {
                // Operators and parentheses are left, let's take a shortcut
                when ((-"+\\-*/(){},|&!").getOrElse { return@oneOf }.content) {
                    "+" -> { treeValue = OperatorPlus }
                    "-" -> { treeValue = OperatorMinus }
                    "*" -> { treeValue = OperatorMultiply }
                    "/" -> { treeValue = OperatorDivide }

                    "(" -> { treeValue = OpenParentheses }
                    ")" -> { treeValue = CloseParentheses }
                    "{" -> { treeValue = OpenCurly }
                    "}" -> { treeValue = CloseCurly }

                    "," -> { treeValue = CommaSep }

                    "|" -> { +"|"; treeValue = OperatorOr }
                    "&" -> { +"&"; treeValue = OperatorAnd }
                    "!" -> { treeValue = Negate }
                    else -> { return@oneOf fail() }
                }
            }, {
                +"=="
                treeValue = OperatorEquals
            }, {
                +"="
                treeValue = Assign
            }, {
                +"<"
                treeValue = OperatorLess
            }, {
                +">"
                treeValue = OperatorGreater
            }, {
                +"<="
                treeValue = OperatorLessEquals
            }, {
                +">="
                treeValue = OperatorGreaterEquals
            }, {
                -!"" // Anything else
                treeValue = Unknown
            })
        }
    }

    val klexParser = Klex.create<KlexLangToken, KlexToken> {
        val whiteSpace = define { oneOf({match<WhiteSpace>()}, {match<Comment>()}) } // Anything used to add whitespace, syntax-wise it's ignored
        val optWS = define { AnyCount {whiteSpace()} }

        val commaSep = define {
            optWS()
            match<CommaSep>()
            optWS()
        }

        var statement by placeholder() // An assignment or expression
        var expression by placeholder() // Anything that results in a value (a + 2), "Example", func()
        var codeScope by placeholder() // Any context that code can be ran from, used for local variables
        var wrappedCodeScope by placeholder() // A statement which wraps a code scope with {}.
        val assignment = define { // identifier = expression
            val iden = match<Identifier>().getOrElse { return@define }
            optWS()
            match<Assign>()
            optWS()
            val expr = expression().getOrElse { return@define }.first.firstFlatValue() as Expression

            treeValue = Assignment(iden.name, expr)
        }

        val returnV = define {
            val iden = match<Identifier>().getOrElse { return@define }.name
            if (iden != "return") return@define fail()
            optWS()
            val expr = expression().getOrElse { return@define }.first.firstFlatValue() as Expression

            treeValue = Return(expr)
        }

        val basicExpression = define { // Any expression that is clearly separated (literal, identifier or full with parentheses)
            oneOf({
                val valToken = match<ValToken>().getOrElse { return@oneOf } // Used for both identifier and literal
                when (valToken) {
                    is ValLiteralToken -> { treeValue = LiteralExpr(valToken.content) }
                    is Identifier -> { treeValue = IdentifierExpr(valToken.name) }
                    else -> { return@oneOf fail() } // Fallback, won't happen because ValToken is sealed
                }
            }, {
                match<OpenParentheses>().getOrElse { return@oneOf }
                optWS()
                val expr = expression().getOrElse { return@oneOf }.first.firstFlatValue() as Expression
                optWS()
                match<CloseParentheses>()
                treeValue = GroupExpr(expr)
            })
        }

        expression = define { // Anything that results in a value (a + 2), "Example", func()
            oneOf({
                // Negate
                match<Negate>().getOrElse { return@oneOf }
                optWS()
                val expr = expression().getOrElse { return@oneOf }.first.firstFlatValue() as Expression
                treeValue = NegateExpr(expr)
            }, {
                // Operator on 2 values
                // TO-DO: Ensure (a + b) wouldn't infinitely try searching, maybe have a subtype of expression like "flatExpression" which excludes recursion except with parentheses
                val basic = basicExpression().getOrElse { return@oneOf }.first.firstFlatValue() as Expression
                optWS()
                val op = match<Operator>().getOrElse { return@oneOf }
                optWS()
                val extended = expression().getOrElse { return@oneOf }.first.firstFlatValue() as Expression

                treeValue = OperatorExpr(basic, extended, op)
            }, {
                // Invoke
                // If function is keyword "function" then this isn't an invocation but a definition
//                val iden = match<Identifier>().getOrElse { return@oneOf }
                val target = basicExpression().getOrElse { return@oneOf }.first.firstFlatValue() as Expression
                match<OpenParentheses>()
                optWS()
                if (target is IdentifierExpr && target.identifier == "function") {
                    // function(paramlist: List<Identifier>) statement
                    val params = mutableListOf<String>()

                    // First parameter
                    Optional {
                        optWS()
                        val paramIden = match<Identifier>().getOrElse { return@Optional }.name
                        params.add(paramIden)
                    }
                    AnyCount {
                        commaSep() // optWs, commaSep, optWs
                        val paramIden = match<Identifier>().getOrElse { return@AnyCount }.name
                        params.add(paramIden)
                    }
                    optWS()
                    match<CloseParentheses>()
                    val block = statement().getOrElse { return@oneOf }.first.firstFlatValue() as Statement

                    treeValue = Function(params, block)
                }
                else {
                    // funcname(paramslist: List<Expression>)

                    val params = mutableListOf<Expression>()

                    // First parameter
                    Optional {
                        optWS()
                        val expr = expression().getOrElse { return@Optional }.first.firstFlatValue() as Expression
                        params.add(expr)
                    }
                    AnyCount {
                        commaSep() // optWs, commaSep, optWs
                        val expr = expression().getOrElse { return@AnyCount }.first.firstFlatValue() as Expression
                        params.add(expr)
                    }
                    optWS()
                    match<CloseParentheses>()

                    treeValue = Invoke(target, params)
                }
            }, {
                basicExpression() // Already creates tree value
            })
        }

        val ifStatement = define { // if($expr) $statement (else $statement)?
            val ifIden = match<Identifier>().getOrElse { return@define }.name == "if"
            if (!ifIden) return@define fail()
            optWS()
            match<OpenParentheses>()
            optWS()
            val expr = expression().getOrElse { return@define }.first.firstFlatValue() as Expression
            optWS()
            match<CloseParentheses>()
            val block = statement().getOrElse { return@define }.first.firstFlatValue() as Statement // Surrounded by optWS()
            val elseIden = match<Identifier>().getOrNull()?.name == "else"
            if (elseIden) {
                val elseBlock = statement().getOrElse { return@define }.first.firstFlatValue() as Statement
                treeValue = If(expr, block, elseBlock)
            }
            else {
                treeValue = If(expr, block, null)
            }
        }

        statement = define { // An assignment or expression
            optWS()
            oneOf(wrappedCodeScope, ifStatement, returnV, assignment, expression)
            optWS()
        }

        codeScope = define {
            val statements = mutableListOf<Statement>()
            AnyCount {
                optWS()
                val st = statement().getOrElse { return@AnyCount }.first.firstFlatValue() as Statement
                optWS()
                statements.add(st)
            }

            treeValue = CodeScope(statements)
        }

        wrappedCodeScope = define {
            match<OpenCurly>()
            val cs = codeScope().getOrElse { return@define }.first.firstFlatValue() as CodeScope
            match<CloseCurly>()

            treeValue = cs
        }

        optWS()
        codeScope() // Root code scope
        optWS()
    }

    fun parseToTree(code: String): Result<CodeScope> {
        val tokens = klexTokenizer.parse(code).getOrElse { return Result.failure(it) }.flattenNullValues().map { it.value!! }
        val ast = klexParser.parse(tokens).getOrElse { return Result.failure(it) }.firstFlatValue() as CodeScope

        return Result.success(ast)
    }

    fun runCode(code: String, variableDef: KlexVariableLookupTable.() -> Unit): Result<Any?> {
        val tree = parseToTree(code).getOrElse { return Result.failure(it) }

        val rootVars = KlexVariableLookupTable().apply(variableDef)

        return KlexExecutionContext(rootVars).execute(tree)
    }
}