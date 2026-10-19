# IrisScript Language Support for VS Code

Official Visual Studio Code extension for the **IrisScript** programming language (v1.0).

---

## Features

- **Rich Syntax Highlighting**: Fully scopes keywords, primitive types, string escapes, numbers, comments, and operator expressions across all VS Code color themes.
- **Run & Compile Buttons**: Easily compile and execute any `.iris` file with a single click using the integrated editor title bar `▶` (Play) and `✔` (Compile) buttons.
- **Real-Time Error Checking**: Live diagnostics engine that catches syntax errors, unhandled control flow paths, and type mismatches as you type, rendering instant squiggly warnings and populating the **Problems** tab.
- **Zero-Configuration Bundled Compiler**: Includes the standalone `iris.jar` compiler directly inside the extension. No separate CLI download or environment PATH configuration required!

---

## Requirements

To run and check `.iris` files with this extension, you only need:

1. **Java Development Kit (JDK 23 or newer)** installed on your machine and available in your system `PATH` (`java` and `javac`).

---

## Extension Settings

For developers working directly on the IrisScript compiler itself, you can override the bundled compiler with your own build:

* `irisscript.compilerPath`: Custom path to a local `iris.jar`, `iris.bat` (Windows), or `iris` (Linux/macOS). If left empty (default), the extension automatically uses the bundled compiler.

---

## Quick Start

1. Create any file ending in `.iris` (e.g. `hello.iris`):
   ```iris
   function main void() {
       variable greeting text = "Hello from IrisScript!\n";
       terminal greeting;
   }
   ```
2. Click the **`▶` (Play)** icon in the top-right corner of the editor to run!
