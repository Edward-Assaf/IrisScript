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
        if (diagnosticsCollector.hasErrors()) {
            return;
        }
    }

    public static void run(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
    }

    public static void help() {
        System.out.println("IrisScript compiler v1.0");
        System.out.println("Usage: irisscript <command> [options]");
        System.out.println("Commands:");
        System.out.println("  build  Build the project and compile source code to Java Source Code");
        System.out.println("  run    Build the project and run it by invoking java runtime environment");
        System.out.println("  help   Show this help message");
        System.out.println("Options:");
        System.out.println("  [-o / --output]    set a specified output directory");
        System.out.println("  [-nc / --no-color] report diagnostics without terminal color codes");
    }

}