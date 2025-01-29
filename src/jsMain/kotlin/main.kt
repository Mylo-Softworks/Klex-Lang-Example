import bindings.Window
import bindings.createDesktopIcon
import execution.NativeFunctionVoid
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

fun main() {
    document.addEventListener("jsReadySignal", {
        createDesktopIcon("Klex lang demo", "assets/img/KlexLogo.png") {
            val window = Window("Klex language demo", createTextEdit())
            window.show()
        }
    })
}

fun executeCode(code: String) {
    KlexLang.runCode(code) {
        // TODO: Standard library
        set("print", NativeFunctionVoid { println(it[0] ?: "") })
        set("printAvailableDebug", NativeFunctionVoid { console.log(this.getAllCurrentContextVariables()) })
    }.getOrElse { it.printStackTrace() }
}

fun createTextEdit(): Element {
    return document.createElement("div").apply {
        classList.add("fullwindow", "large")

        val displayBox = document.createElement("pre").apply {
            classList.add("editor-shared", "fullsize", "no-margin", "no-event")
        }
        val inputBox = (document.createElement("textarea") as HTMLTextAreaElement).apply {
            classList.add("editor-shared", "fullsize", "no-margin", "invisible")
            addEventListener("input", {
                val contentRaw = this.value
                displayBox.innerHTML = getSyntaxColors(contentRaw)
            })

            this.value = """
                // Press ctrl + enter to execute, check javascript console for results
                // To simplify, this language doesn't care about the order of operations in math, except for parentheses.
                // Actual order (if not manually enforced through parentheses) will likely be from right to left

                greet = function(value) { // Functions are defined like variables
	                return "Hello " + value + "!"
                }

                name = "world"

                print(greet(name))
            """.trimIndent()
            dispatchEvent(Event("input"))

            // Sync scrolling
            addEventListener("scroll", {
                displayBox.scrollTop = scrollTop
                displayBox.scrollLeft = scrollLeft
            })

            addEventListener("keydown", {
                if (it !is KeyboardEvent) return@addEventListener
                if (it.key == "Enter" && it.ctrlKey) {
                    executeCode(value)
                }
            })
        }

        appendChild(displayBox)
        appendChild(inputBox)
    }
}

fun getSyntaxColors(input: String): String {
    val result = KlexLang.klexTokenizer.parse(input).getOrElse { return input }.flattenNullValues()
    return result.joinToString("") {
        val color = it.value!!.colorHex
        if (color == null) it.content
        else "<span style=\"color: $color;\">${it.content}</span>"
    }
}