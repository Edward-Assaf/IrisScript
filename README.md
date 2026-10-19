# IrisScript Language (v1.0)

IrisScript is a statically typed, structured programming language designed for clarity, safety, and seamless execution on the Java Virtual Machine. IrisScript compiles directly into clean, idiomatic Java 23 source code, which is then compiled and executed via the host Java toolchain.

This repository contains the complete specification, compiler pipeline, diagnostic engine, code generator, and CLI runtime for IrisScript Version 1.0.

---

## Table of Contents

- [Overview and Core Philosophy](#overview-and-core-philosophy)
- [System Requirements and Installation](#system-requirements-and-installation)
- [Quick Start Guide](#quick-start-guide)
- [Language Specification](#language-specification)
  - [Lexical Structure and Comments](#lexical-structure-and-comments)
  - [Type System and Primitives](#type-system-and-primitives)
  - [Variables and Constants](#variables-and-constants)
  - [Operators and Expression Precedence](#operators-and-expression-precedence)
  - [Control Flow Statements](#control-flow-statements)
  - [Console Output: The Terminal Statement](#console-output-the-terminal-statement)
  - [Functions and Signatures](#functions-and-signatures)
  - [Scope Rules: Unit-Local vs Global](#scope-rules-unit-local-vs-global)
  - [Program Entry Points and Execution Modes](#program-entry-points-and-execution-modes)
- [Diagnostic and Error Code Catalog](#diagnostic-and-error-code-catalog)
- [Compiler Architecture and Pipeline](#compiler-architecture-and-pipeline)
  - [1. Source Management](#1-source-management)
  - [2. Lexical Analysis (IrisLexer)](#2-lexical-analysis-irislexer)
  - [3. Syntactic Analysis (IrisParser)](#3-syntactic-analysis-irisparser)
  - [4. Semantic Analysis and Binding (IrisSemanticAnalyzer)](#4-semantic-analysis-and-binding-irissemanticanalyzer)
  - [5. Java Code Generation (JavaCodeGenerator)](#5-java-code-generation-javacodegenerator)
  - [6. Compilation and Runtime Launcher (CompilerServices)](#6-compilation-and-runtime-launcher-compilerservices)
- [CLI Reference and Tooling](#cli-reference-and-tooling)
- [Comprehensive Code Examples](#comprehensive-code-examples)
- [License](#license)

---

## Overview and Core Philosophy

IrisScript v1.0 is engineered around four guiding principles:

1. **Explicitness and Determinism**: Types, mutability, and cross-file visibility are explicitly declared. Variable shadowing in the same scope and implicit narrowing conversions are disallowed.
2. **Definite Safety**: Non-void functions undergo control flow reachability analysis to guarantee that every possible execution path returns a value.
3. **Frictionless Transpilation**: Rather than emitting raw bytecode or relying on a heavy custom runtime, IrisScript generates clean Java source code utilizing modern Java 23 features, enabling debugging, profiling, and straightforward integration with existing JVM infrastructure.
4. **Resilient Diagnostics**: Lexer, parser, and semantic stages use structured error codes (`E0xxx`, `E1xxx`, `E2xxx`, `E3xxx`, `W2xxx`), secondary labels, and terminal ANSI color formatting to identify faults with precision.

---

## System Requirements and Installation

### Prerequisites

- **Java Development Kit (JDK)**: Version 23 or newer. Ensure `javac` and `java` are available in your system `PATH`.
- **Apache Maven**: Version 3.8 or newer.
- **Operating System**: Windows, Linux, or macOS.

### Building from Source

Clone the repository and compile using Maven:

```bash
git clone https://github.com/Edward-Assaf/irisscript.git
cd irisscript/irisscript-language
mvn clean package
```

This compiles all modules, runs any tests, and packages the compiler into `target/irisscript-language-1.0.jar`.

### Platform Scripts

The compiler includes dedicated command-line launcher scripts:

- **Windows**: `iris.bat`
- **Linux / macOS**: `iris`

Both scripts automatically detect if the project has been built, invoke `mvn compile` if needed, set up the classpath, and launch `irisscript.cli.Main`.

Make the Unix script executable:

```bash
chmod +x ./iris
```

---

## Quick Start Guide

### 1. Script Mode (Hello World)

Create a file named `hello.iris`:

```iris
terminal "Hello, World!\n";
```

Run directly:

```bash
# Windows
.\iris.bat run hello.iris

# Linux / macOS
./iris run hello.iris
```

### 2. Structured Program Mode (Main Function)

Create `program.iris`:

```iris
function main void() {
    variable message text = "Welcome to IrisScript 1.0!\n";
    terminal message;
}
```

Run:

```bash
.\iris.bat run program.iris
```

---

## Language Specification

### Lexical Structure and Comments

IrisScript source code is encoded in UTF-8. 

#### Identifiers
Identifiers must start with an ASCII letter (`a-z`, `A-Z`) or an underscore (`_`), followed by any combination of letters, digits (`0-9`), and underscores.

#### Keywords
The following 19 reserved words cannot be used as identifiers:

```
boolean    break      constant   decimal    else
elseif     false      function   global     if
integer    return     skip       terminal   text
true       variable   void       while
```

#### Comments
Both single-line and multi-line comments are supported:

```iris
// This is a single-line comment.

/*
   This is a multi-line
   block comment.
*/
```

#### String Escapes
Text literals are enclosed in double quotes (`"..."`). The following escape sequences are recognized:
- `\n`: Line feed (newline)
- `\t`: Horizontal tab
- `\r`: Carriage return
- `\"`: Double quote
- `\\`: Backslash

---

### Type System and Primitives

IrisScript provides five built-in types:

| IrisScript Type | JVM Representation | Description | Example Literals |
|:---|:---|:---|:---|
| `integer` | `long` (64-bit signed) | Signed two's complement integer | `0`, `42`, `-1000` |
| `decimal` | `double` (64-bit IEEE 754) | Double-precision floating-point | `3.14`, `0.0`, `-0.5` |
| `text` | `java.lang.String` | Immutable UTF-16 character sequence | `"hello"`, `"line\n"` |
| `boolean` | `boolean` | Logical truth values | `true`, `false` |
| `void` | `void` | Absence of a return value | Allowed only on function return types |

#### Numeric Conversion Semantics
- **Widening (Implicit)**: An `integer` value can be implicitly widened to `decimal` without loss of precision in assignments and arithmetic expressions.
- **Narrowing (Warning)**: Assigning a `decimal` to an `integer` emits warning `W2001` ("Potential loss of precision converting a decimal to an integer").

#### Text Equality Semantics
The equality operators (`==`, `!=`) applied to `text` operands generate `java.util.Objects.equals()` in the target code, ensuring structural equality rather than object identity.

---

### Variables and Constants

Variables and constants are declared at the top level or within block statements.

#### Mutable Variables (`variable`)
```iris
variable counter integer = 0;
counter = counter + 1;
```

#### Immutable Constants (`constant`)
Constants cannot be reassigned once initialized. An initializer is mandatory:
```iris
constant MAX_RETRIES integer = 5;
// MAX_RETRIES = 10; // Error: E2012
```

---

### Operators and Expression Precedence

Expressions are evaluated according to the following precedence hierarchy (lowest to highest):

| Precedence | Category | Operators | Associativity | Supported Types |
|:---|:---|:---|:---|:---|
| 1 (Lowest) | Assignment | `=` | Right | Variable name = Compatible Expression |
| 2 | Logical OR | `\|\|` | Left | `boolean` |
| 3 | Logical AND | `&&` | Left | `boolean` |
| 4 | Bitwise OR | `\|` | Left | `integer` |
| 5 | Bitwise AND | `&` | Left | `integer` |
| 6 | Equality | `==`, `!=` | Left | Compatible types, `text` via `Objects.equals` |
| 7 | Relational | `<`, `<=`, `>`, `>=` | Left | `integer`, `decimal` |
| 8 | Additive | `+`, `-` | Left | `integer`, `decimal` |
| 9 | Multiplicative | `*`, `/`, `%` | Left | `*`, `/` on numbers; `%` strictly on `integer` |
| 10 | Unary | `!`, `+`, `-` | Right | `!` on `boolean`; `+`, `-` on numbers |
| 11 (Highest)| Primary & Call | `()`, literals, identifiers, calls | N/A | Function calls, grouped expressions |

---

### Control Flow Statements

#### Conditionals (`if`, `elseif`, `else`)
Braces are mandatory for block bodies:

```iris
if (score >= 90) {
    terminal "Grade: A\n";
} elseif (score >= 80) {
    terminal "Grade: B\n";
} else {
    terminal "Grade: C or below\n";
}
```

The condition expression must evaluate strictly to `boolean`.

#### Loops (`while`)
The `while` loop continues executing as long as the condition remains `true`:

```iris
variable i integer = 0;
while (i < 10) {
    i = i + 1;
    if (i == 5) {
        skip; // Skips to next iteration (continue)
    }
    if (i == 8) {
        break; // Exits the loop
    }
    terminal i;
    terminal "\n";
}
```

- `break`: Immediately terminates the innermost loop.
- `skip`: Bypasses the rest of the loop body and advances to the next iteration.
- Using `break` or `skip` outside of a loop produces compile error `E2004` or `E2009`.

---

### Console Output: The Terminal Statement

IrisScript uses the dedicated keyword `terminal` for standard output:

```iris
terminal <expression>;
```

- The expression must not be `void` (enforced by `E2025`).
- The transpiler maps `terminal <expression>;` to `System.out.print(<expression>);`.

---

### Functions and Signatures

Functions are defined with the `function` keyword, followed by the function name, return type, formal parameters, and a block body:

```iris
function add integer(variable a integer, variable b integer) {
    return a + b;
}
```

#### Function Rules
1. **Return Type Position**: The return type appears between the function name and the parameter list.
2. **Parameters**: Every parameter must specify `variable` or `constant`, an identifier name, and an explicit type.
3. **Definite Assignment & Returns**: For any non-void function, every possible control flow branch must conclude with a `return` statement carrying an expression (enforced by `E2024`).
4. **Void Functions**: Functions with `void` return type may use bare `return;` statements or omit them. Returning an expression from a `void` function triggers error `E2007`.

---

### Scope Rules: Unit-Local vs Global

IrisScript supports modular compilation across multiple `.iris` files simultaneously.

#### Unit-Local Declarations (Default)
Any function or top-level variable declared without `global` is visible **only within the file in which it is defined**:

```iris
// Defined in FileA.iris
function helper integer() {
    return 42;
}
```
`FileB.iris` cannot access `helper()`.

#### Global Declarations (`global`)
Prefixing a declaration with `global` makes it accessible across all compilation units in the build:

```iris
// Defined in FileA.iris
global variable appVersion text = "1.0.0";

global function computeBonus decimal(variable salary decimal) {
    return salary * 0.15;
}
```

Now `FileB.iris` can call `computeBonus(...)` or read `appVersion` without import statements.

---

### Program Entry Points and Execution Modes

IrisScript supports two execution paradigms:

#### 1. Explicit Main Mode
When at least one function named `main` exists across the compilation units:
- Exactly **one** `main` function must exist.
- It must have the signature: `function main void()` or `global function main void()`.
- It must take **zero parameters** and return `void`.
- Top-level executable statements across modules are not executed automatically in this mode; execution transfers directly to `main()`.

#### 2. Top-Level Script Mode
When no `main` function is defined:
- Top-level executable statements in each `.iris` file are gathered into synthetic entry methods (`__irisRunTopLevelStatements()`).
- All top-level initializers and statements execute sequentially in the order files are supplied to the compiler.

---

## Diagnostic and Error Code Catalog

The compiler features an informative, categorized error reporting system.

### Lexical Diagnostics (`E0xxx`)

| Code | Severity | Description |
|:---|:---|:---|
| `E0001` | Error | Unterminated block comment (`/*` without matching `*/`). |
| `E0002` | Error | Invalid decimal literal (failed floating-point parse). |
| `E0003` | Error | Integer literal too large (exceeds 64-bit signed integer limits). |
| `E0004` | Error | Unterminated text literal (missing closing `"`). |
| `E0005` | Error | Invalid escape sequence inside text literal. |
| `E0006` | Error | Unexpected character encountered. |

### Syntactic Diagnostics (`E1xxx`)

| Code | Severity | Description |
|:---|:---|:---|
| `E1001` | Error | Expected type name. |
| `E1002` | Error | Expected expression. |
| `E1003` | Error | Expected closing parenthesis `)`. |
| `E1004` | Error | Invalid assignment target; target must be an identifier. |
| `E1005` | Error | Expected semicolon `;`. |
| `E1006` | Error | Expected closing brace `}`. |
| `E1007` | Error | Expected opening brace `{`. |
| `E1008` | Error | Expected opening parenthesis `(`. |
| `E1009` | Error | Expected identifier name (function, variable, or parameter). |
| `E1010` | Error | Constant declaration must have an initializer. |
| `E1011` | Error | Expected `variable` or `constant` keyword in parameter declaration. |
| `E1012` | Error | Expected function or variable declaration after `global` modifier. |
| `E1013` | Error | `global` declaration attempted inside a local block (only top-level allowed). |

### Semantic Diagnostics (`E2xxx`, `W2xxx`)

| Code | Severity | Description |
|:---|:---|:---|
| `E2001` | Error | Unknown type name. |
| `E2002` | Error | `void` type is not allowed in this position (e.g. variable or parameter type). |
| `E2003` | Error | Duplicate declaration of symbol in the same scope (includes link to original declaration). |
| `E2004` | Error | `break` statement outside of loop body. |
| `E2005` | Error | Type mismatch between expected type and provided expression type. |
| `E2006` | Error | `return` statement outside of function body. |
| `E2007` | Error | Function declared as `void` cannot return an expression value. |
| `E2008` | Error | Non-void function must return an expression value. |
| `E2009` | Error | `skip` statement outside of loop body. |
| `E2010` | Error | Unresolved identifier name. |
| `E2011` | Error | Cannot assign a value to a function symbol. |
| `E2012` | Error | Cannot reassign a variable declared as `constant`. |
| `E2013` | Error | Operator requires numeric operands (`integer` or `decimal`). |
| `E2014` | Error | Operator requires integer operands (e.g. `%`, `&`, `\|`). |
| `E2015` | Error | Incompatible operand types for equality comparison. |
| `E2016` | Error | Operator requires boolean operands (`&&`, `\|\|`). |
| `E2017` | Error | Function call must target a named function. |
| `E2018` | Error | Expression being invoked is not a function. |
| `E2019` | Error | Argument count mismatch between call site and function declaration. |
| `E2020` | Error | Functions cannot be passed or assigned as first-class variable values. |
| `E2021` | Error | Unsupported literal value type. |
| `E2022` | Error | Unary operator (`+`, `-`) requires a numeric operand. |
| `E2023` | Error | Unary operator (`!`) requires a boolean operand. |
| `E2024` | Error | Control flow analysis failure: not all execution paths return a value. |
| `E2025` | Error | `terminal` statement requires a non-void expression. |
| `W2001` | Warning | Potential loss of precision converting a `decimal` to an `integer`. |

### Build and Execution Diagnostics (`E3xxx`)

| Code | Severity | Description |
|:---|:---|:---|
| `E3001` | Error | Failed to generate Java source code to disk. |
| `E3002` | Error | Invalid program entry: expected exactly one zero-argument `void` `main` function. |
| `E3003` | Error | Program execution was interrupted. |
| `E3004` | Error | Host JDK `javac` compilation or `java` execution failed. |

---

## Compiler Architecture and Pipeline

The compiler is organized as an end-to-end multi-stage pipeline:

```
Source (.iris)
   │
   ▼
[SourceManager] ──────────► Line & column tracking, UTF-8 buffer caching
   │
   ▼
[IrisLexer] ──────────────► TokenStream (Tokens + SourceSpan)
   │
   ▼
[IrisParser] ─────────────► AST (CompilationUnit, Declarations, Statements, Expressions)
   │
   ▼
[IrisSemanticAnalyzer] ───► Two-pass symbol collection, reachability, SemanticModel
   │
   ▼
[JavaCodeGenerator] ──────► Java 23 Source Files (IrisGlobals, Modules, IrisLauncher)
   │
   ▼
[CompilerServices] ───────► Invokes `javac` to build bytecode, then `java` to execute
```

### 1. Source Management
The `SourceManager` loads `.iris` files, assigns continuous source IDs, maps byte offsets to 1-indexed line and column numbers, and caches line breaks to generate source code excerpts for errors.

### 2. Lexical Analysis (`IrisLexer`)
The lexer consumes UTF-8 characters and converts them into a stream of strongly-typed `Token` objects. Each token retains its `TokenType`, lexeme text, optional parsed literal value, and precise `SourceSpan`.

### 3. Syntactic Analysis (`IrisParser`)
A recursive descent parser with precedence climbing constructs an Abstract Syntax Tree (AST) rooted in `CompilationUnit`. The parser performs panic-mode error recovery, synchronizing at statement and top-level declaration boundaries to collect multiple syntax errors per run.

### 4. Semantic Analysis and Binding (`IrisSemanticAnalyzer`)
Semantic analysis is performed in two distinct passes:
1. **Pass 1 (Declaration Collection)**: Registers all top-level functions and variables. Global declarations are defined in `globalScope`, while file-local declarations are placed in their respective unit scopes. Duplicate declarations trigger `E2003` with a reference to the earlier span.
2. **Pass 2 (Body and Expression Checking)**: Evaluates function bodies, statements, and expressions. Validates type compatibility, tracks loop nesting levels for `break`/`skip`, verifies reachability of return statements via `returnsOnEveryPath()`, and records resolutions in `SemanticModel`.

### 5. Java Code Generation (`JavaCodeGenerator`)
The code generator transpiles the verified ASTs into idiomatic Java source code:
- **Identifier Mangling (`JavaNameMangler`)**: All IrisScript identifiers are prefixed with `iris$` and sanitized using unicode sequences (`_uXXXX`), preventing collisions with Java keywords (`class`, `public`, `goto`, etc.).
- **Global Container (`IrisGlobals.java`)**: Holds static variables and functions declared with `global`.
- **Module Classes (`<File>Module<ID>.java`)**: Holds unit-local functions, file-scoped fields, and static initializers.
- **Launcher (`IrisLauncher.java`)**: Pre-loads module classes and dispatches execution to `main()` or top-level statements.

### 6. Compilation and Runtime Launcher (`CompilerServices`)
`CompilerServices` writes generated Java files to the output directory (defaults to `./out`), locates the host `javac` compiler using `java.home`, compiles the generated code with `-encoding UTF-8`, and launches the application with `java -cp <output> irisscript.generated.IrisLauncher`.

---

## CLI Reference and Tooling

### CLI Syntax

```bash
irisscript <command> [options] <files...> [-- <program-args...>]
```

### Commands

| Command | Description |
|:---|:---|
| `build` | Parses, analyzes, and transpiles IrisScript files into Java source code. |
| `run` | Builds the project, compiles generated Java to bytecode, and executes the program. |
| `help` | Displays compiler usage and available options. |

### Options

| Option | Flag Alias | Description |
|:---|:---|:---|
| `--output <dir>` | `-o <dir>` | Specifies the output directory for generated Java code and class files. Default is `./out`. |
| `--no-color` | `-nc` | Disables ANSI color escapes in error and warning diagnostic output. |
| `--` | N/A | Separator indicating all subsequent parameters are forwarded to the executing IrisScript program. |

### CLI Examples

Compile multiple source files into a custom directory:
```bash
.\iris.bat build -o out/src math.iris main.iris
```

Run a program with ANSI color diagnostics disabled:
```bash
.\iris.bat run -nc main.iris
```

Pass arguments to the IrisScript application:
```bash
.\iris.bat run server.iris -- 8080 127.0.0.1
```

---

## Comprehensive Code Examples

### 1. Factorial and Recursion

```iris
function factorial integer(variable n integer) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

function main void() {
    variable num integer = 7;
    terminal "Factorial of ";
    terminal num;
    terminal " is: ";
    terminal factorial(num);
    terminal "\n";
}
```

### 2. Multi-File Global State and Functions

**File: `config.iris`**
```iris
global constant APP_NAME text = "IrisEngine";
global variable currentRuns integer = 0;

global function logRun void(variable label text) {
    currentRuns = currentRuns + 1;
    terminal "[";
    terminal APP_NAME;
    terminal "] Run #";
    terminal currentRuns;
    terminal ": ";
    terminal label;
    terminal "\n";
}
```

**File: `main.iris`**
```iris
function main void() {
    logRun("Initializing core services");
    logRun("Executing primary workload");
    logRun("Shutdown complete");
}
```

Compile and run together:
```bash
.\iris.bat run config.iris main.iris
```

### 3. Numeric Types and Precision Conversion

```iris
function main void() {
    variable intVal integer = 42;
    variable decVal decimal = 10.5;

    // Implicit widening: integer promotes to decimal
    variable sum decimal = intVal + decVal;
    terminal "Sum: ";
    terminal sum; // 52.5
    terminal "\n";

    // Narrowing assignment: triggers warning W2001
    variable truncated integer = decVal;
    terminal "Truncated: ";
    terminal truncated; // 10
    terminal "\n";
}
```

### 4. While Loops with Skip and Break

```iris
function main void() {
    variable count integer = 0;
    terminal "Even numbers up to 10:\n";

    while (count < 20) {
        count = count + 1;

        if (count % 2 != 0) {
            skip; // Skip odd numbers
        }

        if (count > 10) {
            break; // Stop at 10
        }

        terminal "  -> ";
        terminal count;
        terminal "\n";
    }
}
```