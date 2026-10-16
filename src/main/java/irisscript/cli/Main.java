package irisscript.cli;

import irisscript.diagnostics.Diagnostic;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.diagnostics.format.ANSIFormatter;
import irisscript.diagnostics.format.DiagnosticsFormatter;
import irisscript.source.SourceManager;

public class Main {

    public static void main(String[] args) {
        CLIArguments arguments = CLIParser.parse(args);
        if (arguments.hasError()) {
            System.out.println(arguments.errorMessage());
            System.exit(1);
        }
        SourceManager sourceManager = new SourceManager();
        CompilerOptions options = arguments.options();
        DiagnosticsCollector diagnosticsCollector = new DiagnosticsCollector();
        DiagnosticsFormatter diagnosticsFormatter = new ANSIFormatter(sourceManager, options.usesColor());
        for (Diagnostic diagnostic : diagnosticsCollector.getDiagnostics()) {
            System.err.println(diagnosticsFormatter.format(diagnostic));
        }
        if (diagnosticsCollector.hasErrors()) {
            System.exit(1);
        }
    }

}