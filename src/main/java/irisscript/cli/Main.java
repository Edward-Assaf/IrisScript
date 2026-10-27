package irisscript.cli;

import java.io.IOException;
import java.nio.file.Paths;

import irisscript.build.CompilerServices;
import irisscript.diagnostics.Diagnostic;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.diagnostics.format.ANSIFormatter;
import irisscript.diagnostics.format.DiagnosticsFormatter;
import irisscript.source.SourceManager;

public class Main {

    public static void main(String[] args) {
        CLIArguments arguments = CLIParser.parse(args);
        if (arguments.hasError()) {
            System.err.println(arguments.errorMessage());
            System.exit(1);
        }
        SourceManager sourceManager = new SourceManager();
        for (String sourceFilePath : arguments.sourceFilePaths()) {
            try {
                sourceManager.load(Paths.get(sourceFilePath));
            } catch (IOException e) {
                System.err.println("Error while loading source file: " + sourceFilePath.toString());
                System.exit(1);
            }
        }
        CompilerOptions options = arguments.options();
        DiagnosticsCollector diagnosticsCollector = new DiagnosticsCollector();
        DiagnosticsFormatter diagnosticsFormatter = new ANSIFormatter(sourceManager, options.usesColor());
        int exitCode = 0;
        switch (arguments.command()) {
            case BUILD:
                CompilerServices.build(sourceManager, options, diagnosticsCollector);
                break;
            case RUN:
                exitCode = CompilerServices.run(sourceManager, options, diagnosticsCollector);
                break;
            case HELP:
                CompilerServices.help();
                break;
            default:
                throw new IllegalStateException("Unexpected command: " + arguments.command());
        }
        System.out.println();
        for (Diagnostic diagnostic : diagnosticsCollector.getDiagnostics()) {
            System.err.println(diagnosticsFormatter.format(diagnostic));
        }
        if (diagnosticsCollector.hasErrors()) {
            System.exit(1);
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

}