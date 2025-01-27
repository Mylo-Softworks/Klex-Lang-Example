import kotlinx.browser.document

fun main() {
    document.asDynamic().klexlang = KlexLang // Make available to js like traditional script
}