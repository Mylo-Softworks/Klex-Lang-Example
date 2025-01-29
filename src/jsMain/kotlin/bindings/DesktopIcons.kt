//@file:JsModule("../../os/desktopicons.js")

package bindings

@JsName("createDesktopIcon")
//@JsNonModule
external fun createDesktopIcon(name: String, icon: String, onclick: () -> Unit)