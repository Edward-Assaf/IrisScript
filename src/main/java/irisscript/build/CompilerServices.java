package irisscript.build;

import java.util.ArrayList;
import java.util.List;

import irisscript.ast.CompilationUnit;
import irisscript.cli.CompilerOptions;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.lexer.IrisLexer;
import irisscript.lexer.TokenStream;
import irisscript.parser.IrisParser;
import irisscript.semantic.IrisSemanticAnalyzer;
import irisscript.source.SourceManager;

public final class CompilerServices {

    public static void build(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
        IrisLexer lexer = new IrisLexer(diagnosticsCollector);
        IrisParser parser = new IrisParser(diagnosticsCollector);
        IrisSemanticAnalyzer analyzer = new IrisSemanticAnalyzer(diagnosticsCollector);
        List<CompilationUnit> units = new ArrayList<>();
        for (int i = 0; i < sourceManager.getNumberOfFiles(); i++) {
            TokenStream tokens = lexer.tokenize(sourceManager.get(i));
            CompilationUnit unit = parser.parse(tokens);
            units.add(unit);
        }
        analyzer.analyze(units);
    }

    public static void run(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
    }

}