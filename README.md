# IrisScript Language (v1.1.2)

IrisScript is a statically typed, structured programming language designed for clarity, safety, and seamless execution on the Java Virtual Machine. IrisScript compiles into Java source code, which is then compiled and executed using an installed Java Development Kit (JDK).

This repository contains the language specification, compiler pipeline, diagnostic engine, code generator, CLI runtime, and VS Code language support for IrisScript.

---

## Table of Contents

- [Overview and Core Philosophy](#overview-and-core-philosophy)
- [System Requirements and Installation](#system-requirements-and-installation)
  - [Prerequisites](#prerequisites)
  - [Building from Source](#building-from-source)
  - [Platform Scripts](#platform-scripts)
  - [JDK Discovery and Configuration](#jdk-discovery-and-configuration)
- [Quick Start Guide](#quick-start-guide)
- [Language Specification](#language-specification)
  - [Lexical Structure and Comments](#lexical-structure-and-comments)
  - [Type System and Primitives](#type-system-and-primitives)
  - [Variables and Constants](#variables-and-constants)
  - [Operators and Expression Precedence](#operators-and-expression-precedence)
  - [Control Flow Statements](#control-flow-statements)
  - [Loops (while)](#loops-while)
  - [Arrays](#arrays)
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
  - [7. JDK Discovery (JdkLocator)](#7-jdk-discovery-jdklocator)
- [CLI Reference](#cli-reference)
- [Tooling and VS Code Extension](#tooling-and-vs-code-extension)
- [Comprehensive Code Examples](#comprehensive-code-examples)
- [License](#license)

---

## Overview and Core Philosophy

IrisScript is built around four guiding principles:

1. Explicitness and Determinism: Types, mutability, and cross-file visibility are explicitly declared. Variable shadowing in the same scope and implicit narrowing conversions are disallowed.
2. Definite Safety: Non-void functions undergo control-flow reachability analysis to ensure every possible execution path returns a value.
3. Frictionless Transpilation: IrisScript generates Java source code, enabling debugging, profiling, and integration with existing JVM infrastructure.
4. Resilient Diagnostics: Lexer, parser, and semantic stages use structured error codes (E0xxx, E1xxx, E2xxx, E3xxx, W2xxx) and source locations to identify faults precisely.

---

## System Requirements and Installation

### Prerequisites

- Java Development Kit (JDK): Version 23 or newer, provided this matches the project's configured Java target. IrisScript needs both java and javac to compile and execute generated Java programs.
- Apache Maven: Version 3.8 or newer when building from source.
- Operating System: Windows, Linux, or macOS.

A JDK does not need to be discoverable exclusively through the system PATH. IrisScript's JDK discovery mechanism checks configured environment variables, the currently running JVM's installation, and executable locations in PATH.

### Building from Source

Clone the repository and build it with Maven:

    git clone https://github.com/Edward-Assaf/IrisScript.git
    cd IrisScript
    mvn clean package

The build compiles the project, runs configured tests, and packages the compiler into a JAR under target/.

Adjust the working directory if your repository's Maven project is located in a subdirectory.

### Platform Scripts

IrisScript can be launched through platform-specific scripts:

- Windows: iris.bat
- Linux/macOS: iris

These scripts provide a convenient CLI entry point. Their actual behavior depends on the scripts included in the distribution.

On Linux or macOS, if the executable permission was not preserved:

    chmod +x ./iris

Example on Linux/macOS:

    ./iris run hello.iris

Example on Windows PowerShell:

    .\iris.bat run hello.iris

### JDK Discovery and Configuration

IrisScript uses JdkLocator to locate a complete JDK instead of assuming that java and javac are always located under the current JVM's java.home.

The discovery process checks candidate locations in this order:

1. IRISSCRIPT_JDK_HOME — an IrisScript-specific override.
2. JAVA_HOME — the standard Java installation environment variable.
3. java.home — the installation associated with the currently running JVM, followed by its parent directory where applicable.
4. PATH — directories containing executable files.

A candidate JDK is accepted only when both the Java runtime and Java compiler executables are present in the same bin directory.

Typical JDK layout:

    jdk/
    ├── bin/
    │   ├── java
    │   └── javac
    └── ...

On Windows, the executables are typically named java.exe and javac.exe.

#### Configuring a JDK

Set either IRISSCRIPT_JDK_HOME or JAVA_HOME to the JDK root directory, not its bin directory.

Windows PowerShell:

    $env:JAVA_HOME = "C:\path\to\your\jdk"

Or:

    $env:IRISSCRIPT_JDK_HOME = "C:\path\to\your\jdk"

Linux:

    export JAVA_HOME="/path/to/your/jdk"

Or:

    export IRISSCRIPT_JDK_HOME="/path/to/your/jdk"

macOS:

    export JAVA_HOME="/path/to/your/jdk"

Or:

    export IRISSCRIPT_JDK_HOME="/path/to/your/jdk"

Replace the example paths with the actual JDK installation directory.

IRISSCRIPT_JDK_HOME takes precedence over JAVA_HOME. If a candidate is invalid or lacks either required executable, discovery continues to the next candidate.

IrisScript does not scan the entire filesystem. It checks known configuration locations and directories listed in PATH.

Important: JDK discovery inside IrisScript occurs only after the compiler JVM has started. If a launcher starts the compiler using java -jar, that initial Java invocation must itself be able to locate Java. Internal discovery cannot resolve a missing Java runtime before the compiler starts.

---

## Quick Start Guide

### 1. Script Mode (Hello World)

Create a file named hello.iris:

    terminal "Hello, World!\n";

Run directly:

Windows:

    .\iris.bat run hello.iris

Linux/macOS:

    ./iris run hello.iris

### 2. Structured Program Mode (Main Function)

Create program.iris:

    function main void() {
        variable message text = "Welcome to IrisScript 1.0!\n";
        terminal message;
    }

Run:

Windows:

    .\iris.bat run program.iris

Linux/macOS:

    ./iris run program.iris

---

## Language Specification

### Lexical Structure and Comments

IrisScript source code is encoded in UTF-8.

#### Identifiers

Identifiers must start with an ASCII letter (a-z, A-Z) or an underscore (_), followed by any combination of letters, digits (0-9), and underscores.

#### Keywords

The following reserved words cannot be used as identifiers:

    boolean    break      constant   decimal    else
    elseif     false      function   global     if
    integer    return     skip       terminal   text
    true       variable   void       while

#### Comments

Both single-line and multi-line comments are supported.

Single-line comment:

    // This is a single-line comment.

Multi-line comment:

    /*
       This is a multi-line
       block comment.
    */

#### String Escapes

Text literals are enclosed in double quotes. The following escape sequences are recognized:

- \n: Line feed (newline)
- \t: Horizontal tab
- \r: Carriage return
- \": Double quote
- \\: Backslash

---

### Type System and Primitives

IrisScript provides five built-in types:

| IrisScript Type | JVM Representation | Description | Example Literals |
|:---|:---|:---|:---|
| integer | long (64-bit signed) | Signed two's complement integer | 0, 42, -1000 |
| decimal | double (64-bit IEEE 754) | Double-precision floating-point | 3.14, 0.0, -0.5 |
| text | java.lang.String | Immutable UTF-16 character sequence | "hello", "line\n" |
| boolean | boolean | Logical truth values | true, false |
| void | void | Absence of a return value | Allowed only on function return types |

#### Numeric Conversion Semantics

- Widening (Implicit): An integer value can be implicitly widened to decimal in assignments and arithmetic expressions.
- Narrowing (Warning): Assigning a decimal to an integer emits warning W2001, indicating potential loss of precision.

#### Text Equality Semantics

The equality operators (==, !=) applied to text operands generate java.util.Objects.equals() in the target code, ensuring structural equality rather than object identity.

---

### Variables and Constants

Variables and constants are declared at the top level or within block statements.

#### Mutable Variables (variable)

    variable counter integer = 0;
    counter = counter + 1;

#### Immutable Constants (constant)

Constants cannot be reassigned once initialized. An initializer is mandatory:

    constant MAX_RETRIES integer = 5;

Reassigning MAX_RETRIES produces error E2012.

---

### Operators and Expression Precedence

Expressions are evaluated according to the following precedence hierarchy, from lowest to highest:

| Precedence | Category | Operators | Associativity | Supported Types |
|:---|:---|:---|:---|:---|
| 1 (Lowest) | Assignment | =, +=, -=, *=, /=, %=, &=, \|= | Right | Variable name = Compatible Expression |
| 2 | Logical OR | || | Left | boolean |
| 3 | Logical AND | && | Left | boolean |
| 4 | Bitwise OR | | | Left | integer |
| 5 | Bitwise AND | & | Left | integer |
| 6 | Equality | ==, != | Left | Compatible types; text uses Objects.equals |
| 7 | Relational | <, <=, >, >= | Left | integer, decimal |
| 8 | Additive | +, - | Left | integer, decimal; + also concatenates text |
| 9 | Multiplicative | *, /, % | Left | * and / on numbers; % strictly on integer |
| 10 | Unary | !, +, -, ++, -- | Right | ! on boolean; +, -, ++, -- on numbers |
| 11 (Highest) | Primary and Call | (), literals, identifiers, calls | N/A | Function calls and grouped expressions |

---

### Control Flow Statements

#### Conditionals (if, elseif, else)

Braces are mandatory for block bodies:

    if (score >= 90) {
        terminal "Grade: A\n";
    } elseif (score >= 80) {
        terminal "Grade: B\n";
    } else {
        terminal "Grade: C or below\n";
    }

The condition expression must evaluate strictly to boolean.

#### Loops (while)

The while loop continues executing as long as its condition remains true:

    variable i integer = 0;

    while (i < 10) {
        i = i + 1;

        if (i == 5) {
            skip;
        }

        if (i == 8) {
            break;
        }

        terminal i;
        terminal "\n";
    }

- break: Immediately terminates the innermost loop.
- skip: Bypasses the rest of the loop body and advances to the next iteration.
- Using break or skip outside a loop produces a semantic error.

---

### Arrays

Arrays in IrisScript represent a list or a sequence of ordered and indexed elements. Arrays come in four basic types, integer[], decimal[], text[], boolean[]. Arrays require a fixed size to be determined at declaration. It can be determined in two ways, either via a size-declaration:

    variable numbers integer[10]; // an array of integers with size 10 (10 elements)
    variable names text[5];

Or via the array literal initialization:

    variable numbers integer[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}; // an array of integers with size 10 (10 elements)
    variable flags boolean[] = {true, false, true, false};

Similar to variables, arrays can also either be declared as variable arrays or constant arrays. A constant array has to be initialized with an array literal initialization:

    constant names text[] = {"Alice", "Bob", "Charlie", "David", "Eve"};
    constant numbers integer[]; // error

Constant arrays, like constant variables, are not reassignable. However, this does not prohibit reassigning the elements of a constant array, as the constraint only applies to the array reference. Also, an initialized array cannot specify a fixed-size:

    variable test integer[] = {1, 2, 3};
    variable test2 integer[3] = {1, 2, 3}; // error

IrisScript supports multi-dimensional arrays in the same way. A simple 2D array can be defined as follows:

    variable test integer[][] = {{1,2},{3,4},{5,6,7}};
    variable test2 integer[2][3];
    variable test3 integer[2][]; // error, no partial-size declarations are allowed
    variable test4 integer[2][2] = {{1,2},{3,4},{5,6}}; // error, size-declared arrays can't be initialized

Elements of an array in IrisScript can be accessed using the index operator just like any other language. Some examples:

    arr[i]; // 1D-array element access
    arr[i][j]; // 2D-array element access

Formally, if arr is an n-dimensional array, arr[i] returns the i'th (n-1)-dimensional element of arr. Note that indices can be used as well with text variables to return a specific character.

    variable x text = "hello";
    variable part text = x[4]; // part = "o"

---

### Console Output: The Terminal Statement

IrisScript uses the dedicated keyword terminal for standard output:

    terminal <expression>;

- The expression must not be void.
- The transpiler maps terminal <expression>; to System.out.print(<expression>);.

---

### Functions and Signatures

Functions are defined with the function keyword, followed by the function name, return type, formal parameters, and a block body:

    function add integer(variable a integer, variable b integer) {
        return a + b;
    }

#### Function Rules

1. The return type appears between the function name and the parameter list.
2. Every parameter must specify variable or constant, an identifier name, and an explicit type.
3. For any non-void function, every possible control-flow branch must conclude with a return statement carrying an expression.
4. Void functions may use bare return statements or omit them. Returning an expression from a void function triggers an error.

---

### Scope Rules: Unit-Local vs Global

IrisScript supports modular compilation across multiple .iris files simultaneously.

#### Unit-Local Declarations (Default)

Any function or top-level variable declared without global is visible only within the file in which it is defined.

Example in FileA.iris:

    function helper integer() {
        return 42;
    }

FileB.iris cannot access helper() unless it is declared global.

#### Global Declarations (global)

Prefixing a declaration with global makes it accessible across all compilation units in the build:

    global variable appVersion text = "1.0.0";

    global function computeBonus decimal(variable salary decimal) {
        return salary * 0.15;
    }

Other source files in the same compilation can access these declarations without import statements.

---

### Program Entry Points and Execution Modes

IrisScript supports two execution paradigms.

#### 1. Explicit Main Mode

When at least one function named main exists across the compilation units:

- Exactly one main function must exist.
- It must have the signature function main void() or global function main void().
- It must take zero parameters and return void.
- Top-level executable statements across modules are not executed automatically in this mode; execution transfers directly to main().

#### 2. Top-Level Script Mode

When no main function is defined:

- Top-level executable statements in each .iris file are gathered into synthetic entry methods.
- All top-level initializers and statements execute sequentially in the order files are supplied to the compiler.

---

## Diagnostic and Error Code Catalog

The compiler features categorized error reporting.

### Lexical Diagnostics (E0xxx)

| Code | Severity | Description |
|:---|:---|:---|
| E0001 | Error | Unterminated block comment. |
| E0002 | Error | Invalid decimal literal. |
| E0003 | Error | Integer literal too large. |
| E0004 | Error | Unterminated text literal. |
| E0005 | Error | Invalid escape sequence inside text literal. |
| E0006 | Error | Unexpected character encountered. |

### Syntactic Diagnostics (E1xxx)

| Code | Severity | Description |
|:---|:---|:---|
| E1001 | Error | Expected type name. |
| E1002 | Error | Expected expression. |
| E1003 | Error | Expected closing parenthesis. |
| E1004 | Error | Invalid assignment target; target must be an identifier or an index. |
| E1005 | Error | Expected semicolon. |
| E1006 | Error | Expected closing brace. |
| E1007 | Error | Expected opening brace. |
| E1008 | Error | Expected opening parenthesis. |
| E1009 | Error | Expected identifier name. |
| E1010 | Error | Constant declaration must have an initializer. |
| E1011 | Error | Expected variable or constant keyword in parameter declaration. |
| E1012 | Error | Expected function or variable declaration after global. |
| E1013 | Error | Global declaration attempted inside a local block. |
| E1014 | Error | Expected closing bracket for array declaration. |
| E1015 | Error | Expected closing brace after array literal. |
| E1016 | Error | Expected closing bracket for index expression. |

### Semantic Diagnostics (E2xxx, W2xxx)

| Code | Severity | Description |
|:---|:---|:---|
| E2001 | Error | Unknown type name. |
| E2002 | Error | Void type is not allowed in this position. |
| E2003 | Error | Duplicate declaration of a symbol in the same scope. |
| E2004 | Error | Break statement outside a loop body. |
| E2005 | Error | Type mismatch between expected type and provided expression type. |
| E2006 | Error | Return statement outside a function body. |
| E2007 | Error | Void function cannot return an expression value. |
| E2008 | Error | Non-void function must return an expression value. |
| E2009 | Error | Skip statement outside a loop body. |
| E2010 | Error | Unresolved identifier name. |
| E2011 | Error | Cannot assign a value to a function symbol. |
| E2012 | Error | Cannot reassign a constant. |
| E2013 | Error | Operator requires numeric operands. |
| E2014 | Error | Operator requires integer operands. |
| E2015 | Error | Incompatible operand types for equality comparison. |
| E2016 | Error | Operator requires boolean operands. |
| E2017 | Error | Function call must target a named function. |
| E2018 | Error | Expression being invoked is not a function. |
| E2019 | Error | Function argument count mismatch. |
| E2020 | Error | Functions cannot be passed or assigned as first-class variable values. |
| E2021 | Error | Unsupported literal value type. |
| E2022 | Error | Unary plus or minus requires a numeric operand. |
| E2023 | Error | Unary logical NOT requires a boolean operand. |
| E2024 | Error | Not all execution paths return a value in a non-void function. |
| E2025 | Error | Terminal statement requires a non-void expression. |
| E2026 | Error | Cannot infer the type of an empty array literal. |
| E2027 | Error | Array literal elements must have compatible types. |
| E2028 | Error | Array size must be an integer expression. |
| E2029 | Error | Increment and decrement operators require an identifier or an index operand. |
| E2030 | Error | Increment and decrement operators require a numeric operand. |
| E2031 | Error | Arrays with an explicit size cannot have an initializer. |
| E2032 | Error | Size-declaration for arrays is only permitted in declaration statements. |
| E2033 | Error | Array size must be non-negative. |
| E2034 | Error | Indices must be integer expressions. |
| E2035 | Error | Index target must be an array or a text. |
| E2036 | Error | Multidimensional array has partial size-declaration. |
| W2001 | Warning | Potential loss of precision converting decimal to integer. |

### Build and Execution Diagnostics (E3xxx)

| Code | Severity | Description |
|:---|:---|:---|
| E3001 | Error | Failed to generate Java source code to disk. |
| E3002 | Error | Invalid program entry point. |
| E3003 | Error | Program execution was interrupted. |
| E3004 | Error | JDK discovery, Java compilation, or Java execution failed. |

---

## Compiler Architecture and Pipeline

The compiler is organized as an end-to-end multi-stage pipeline:

    Source (.iris)
       |
       v
    SourceManager
       |
       v
    IrisLexer
       |
       v
    IrisParser
       |
       v
    IrisSemanticAnalyzer
       |
       v
    JavaCodeGenerator
       |
       v
    CompilerServices
       |
       +--> JdkLocator
       |
       +--> javac
       |
       +--> java

### 1. Source Management

SourceManager loads .iris files, assigns source IDs, maps offsets to line and column numbers, and caches line-break information to generate source excerpts for diagnostics.

### 2. Lexical Analysis (IrisLexer)

The lexer consumes source characters and converts them into strongly typed Token objects. Each token retains its TokenType, lexeme, optional parsed literal value, and SourceSpan.

### 3. Syntactic Analysis (IrisParser)

The recursive-descent parser constructs an Abstract Syntax Tree (AST) rooted in CompilationUnit. It uses expression precedence rules and error recovery to report syntax errors.

### 4. Semantic Analysis and Binding (IrisSemanticAnalyzer)

Semantic analysis validates declarations, types, scopes, function calls, control flow, and return paths.

The analyzer collects declarations and then checks function bodies and expressions. It records symbol resolutions in SemanticModel and reports semantic errors and warnings.

### 5. Java Code Generation (JavaCodeGenerator)

The code generator translates validated ASTs into Java source code.

Generated code can include:

- A global container for global declarations.
- Module classes for file-local declarations.
- A launcher class that dispatches execution to main() or top-level statements.

Identifier mangling prevents IrisScript identifiers from colliding with Java keywords and generated names.

### 6. Compilation and Runtime Launcher (CompilerServices)

CompilerServices coordinates build and run operations.

The build operation generates Java source files in the configured output directory.

The run operation:

1. Locates a valid JDK through JdkLocator.
2. Uses the discovered javac executable to compile generated Java sources.
3. Stops if compilation fails.
4. Uses java from the same JDK to execute the generated launcher.
5. Forwards program arguments and returns the program's exit code.

Using a single toolchain avoids compiling with one JDK and running with another.

### 7. JDK Discovery (JdkLocator)

JdkLocator encapsulates the search for a complete Java Development Kit.

It checks, in priority order:

1. IRISSCRIPT_JDK_HOME.
2. JAVA_HOME.
3. The current JVM's java.home and its parent directory.
4. Directories listed in PATH.

It uses platform-aware Java path APIs, the platform's PATH separator, and platform-appropriate executable names. It does not hardcode common installation paths or scan the entire filesystem.

A candidate is accepted only if java and javac are present in the same bin directory. If no valid candidate is found, JdkLocator throws an IOException explaining that a complete JDK could not be found.

---

## CLI Reference

### CLI Syntax

    iris <command> [options] <files...> [-- <program-args...>]

### Commands

| Command | Description |
|:---|:---|
| build | Parses, analyzes, and transpiles IrisScript files into Java source code. |
| run | Builds the project, compiles generated Java to bytecode, and executes the program. |
| help | Displays compiler usage and available options. |

### Options

| Option | Alias | Description |
|:---|:---|:---|
| --output <dir> | -o <dir> | Specifies the output directory. |
| --no-color | -nc | Disables ANSI color output. |
| -- | N/A | Forwards subsequent arguments to the IrisScript program. |

### CLI Examples

Build multiple source files:

    iris build -o out math.iris main.iris

Run with color disabled:

    iris run -nc main.iris

Pass arguments to the program:

    iris run server.iris -- 8080 127.0.0.1

---

## Comprehensive Code Examples

### 1. Factorial and Recursion

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

### 2. Multi-File Global State and Functions

File: config.iris

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

File: main.iris

    function main void() {
        logRun("Initializing core services");
        logRun("Executing primary workload");
        logRun("Shutdown complete");
    }

Run both files together:

    iris run config.iris main.iris

### 3. Numeric Types and Precision Conversion

    function main void() {
        variable intVal integer = 42;
        variable decVal decimal = 10.5;

        variable sum decimal = intVal + decVal;
        terminal "Sum: ";
        terminal sum;
        terminal "\n";

        variable truncated integer = decVal;
        terminal "Truncated: ";
        terminal truncated;
        terminal "\n";
    }

### 4. While Loops with Skip and Break

    function main void() {
        variable count integer = 0;
        terminal "Even numbers up to 10:\n";

        while (count < 20) {
            count = count + 1;

            if (count % 2 != 0) {
                skip;
            }

            if (count > 10) {
                break;
            }

            terminal "  -> ";
            terminal count;
            terminal "\n";
        }
    }

---

## Tooling and VS Code Extension

IrisScript includes a VS Code extension located in the repository's VS Code extension directory.

### Features

- Syntax highlighting for IrisScript keywords, types, operators, strings, numbers, and comments.
- Run and Compile buttons in the editor title bar for .iris files.
- Real-time diagnostics displayed through editor squiggles and the Problems panel.
- Compiler integration through the IrisScript CLI.

### JDK Requirements

The extension's Java-launch mechanism and the compiler's internal JDK discovery are separate responsibilities.

If the extension launches the compiler using java -jar, Java must be available to that initial launch command. JdkLocator operates only after the compiler JVM has started.

For compiling and executing IrisScript programs, a complete JDK containing both java and javac must be discoverable through the supported configuration paths.