//@file:JsModule("../../os/windows.js")
//@file:JsNonModule

package bindings

import org.w3c.dom.Element

//@JsNonModule
@JsName("Window")
external class Window(title: String, contentEl: Element) {
    var element: Element?
    var contentElement: Element?

    fun show()
    fun close()
}