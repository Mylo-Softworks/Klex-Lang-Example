# Klex-Lang-Example
An example scripting language parsed in [Klex], using Kotlin/JS to run in the browser.

You can try it out [here](https://mylosoftworks.com/)

Below is a simple program which creates a function named "greet" and then
```
greet = function(value) { // Functions are defined like variables
	return "Hello " + value + "!"
}

name = "world"

greet(name)
```

## This project
* jsMain: Contains the main entrypoint, and bindings for the Mylo-Softworks website.
* commonMain: Contains the actual source code for the parser and interpreter.

[Klex]: https://github.com/mylo-softworks/klex