@echo off
setlocal

:: 1. Finds the folder where this batch script lives
set "SCRIPT_DIR=%~dp0"
set "CLASSES_DIR=%SCRIPT_DIR%target\classes"
set "JAR_FILE=%SCRIPT_DIR%target\irisscript-language-1.0.jar"

:: 2. Determines the classpath (prefers compiled classes, fallbacks to JAR)
if exist "%CLASSES_DIR%" (
    set "CP=%CLASSES_DIR%"
) else if exist "%JAR_FILE%" (
    set "CP=%JAR_FILE%"
) else (
    echo [Iris] Project has not been built yet. Running mvn compile...
    mvn -f "%SCRIPT_DIR%pom.xml" compile
    set "CP=%CLASSES_DIR%"
)

:: 3. Runs the Java application and forwards all arguments (%*)
java -cp "%CP%" irisscript.cli.Main %*

:: 4. Preserves and returns the Java program's exit code
exit /b %ERRORLEVEL%