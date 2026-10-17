package irisscript.build;

import java.util.List;

import irisscript.cli.CompilerOptions;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.lexer.IrisLexer;
import irisscript.lexer.Token;
import irisscript.source.SourceManager;

public final class CompilerServices {

    public static void build(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
        IrisLexer lexer = new IrisLexer(diagnosticsCollector);
        for (int i = 0; i < sourceManager.getNumberOfFiles(); i++) {
            List<Token> tokens = lexer.tokenize(sourceManager.get(i));
            for (Token token : tokens) {
                System.out.println(token.type());
            }
        }
    }

    public static void run(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
    }

}