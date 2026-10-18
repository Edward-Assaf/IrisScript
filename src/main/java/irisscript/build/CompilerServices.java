package irisscript.build;

import irisscript.ast.CompilationUnit;
import irisscript.cli.CompilerOptions;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.lexer.IrisLexer;
import irisscript.lexer.TokenStream;
import irisscript.parser.IrisParser;
import irisscript.source.SourceManager;

public final class CompilerServices {

    public static void build(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
        IrisLexer lexer = new IrisLexer(diagnosticsCollector);
        IrisParser parser = new IrisParser(diagnosticsCollector);
        for (int i = 0; i < sourceManager.getNumberOfFiles(); i++) {
            TokenStream tokens = lexer.tokenize(sourceManager.get(i));
            CompilationUnit unit = parser.parse(tokens);
        }
    }

    public static void run(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
    }

}