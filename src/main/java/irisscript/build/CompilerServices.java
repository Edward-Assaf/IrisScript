package irisscript.build;

import irisscript.cli.CompilerOptions;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.lexer.IrisLexer;
import irisscript.lexer.TokenStream;
import irisscript.source.SourceManager;

public final class CompilerServices {

    public static void build(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
        IrisLexer lexer = new IrisLexer(diagnosticsCollector);
        for (int i = 0; i < sourceManager.getNumberOfFiles(); i++) {
            TokenStream tokens = lexer.tokenize(sourceManager.get(i));

        }
    }

    public static void run(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
    }

}