package irisscript.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import irisscript.ast.CompilationUnit;
import irisscript.cli.CompilerOptions;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.diagnostics.DiagnosticCode;
import irisscript.diagnostics.DiagnosticSeverity;
import irisscript.codegen.GeneratedSource;
import irisscript.codegen.JavaCodeGenerator;
import irisscript.semantic.SemanticModel;
import irisscript.lexer.IrisLexer;
import irisscript.lexer.TokenStream;
import irisscript.parser.IrisParser;
import irisscript.semantic.IrisSemanticAnalyzer;
import irisscript.source.SourceManager;
import irisscript.source.SourceSpan;

public final class CompilerServices {

    private static final String GENERATED_PACKAGE = "irisscript.generated";

    private static final String LAUNCHER_CLASS = GENERATED_PACKAGE + ".IrisLauncher";

    private CompilerServices() {
    }

    public static void build(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
        AnalysisResult analysis = analyze(sourceManager, diagnosticsCollector);
        List<CompilationUnit> units = analysis.units();
        if (diagnosticsCollector.hasErrors() || units.isEmpty()) {
            return;
        }
        Path outputDirectory = Path.of(options.getOutputDirectory()).toAbsolutePath().normalize();
        Path generatedDirectory = outputDirectory.resolve("irisscript/generated");
        try {
            deleteTree(generatedDirectory);
            List<GeneratedSource> generatedSources = new JavaCodeGenerator(analysis.semanticModel())
                    .generate(units, sourceManager);
            for (GeneratedSource generated : generatedSources) {
                Path outputFile = outputDirectory.resolve(generated.fileName()).normalize();
                if (!outputFile.startsWith(outputDirectory)) {
                    throw new IOException("Generated output path escapes the output directory");
                }
                Files.createDirectories(outputFile.getParent());
                Files.writeString(outputFile, generated.source(), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException exception) {
            diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode("E3001"),
                    "Failed to generate Java source: " + exception.getMessage(), units.get(0).span());
        }
    }

    /**
     * Builds, compiles, and launches the program. Returns the program's exit code.
     */
    public static int run(SourceManager sourceManager, CompilerOptions options,
            DiagnosticsCollector diagnosticsCollector) {
        build(sourceManager, options, diagnosticsCollector);
        if (diagnosticsCollector.hasErrors() || sourceManager.getNumberOfFiles() == 0) {
            return 1;
        }
        Path outputDirectory = Path.of(options.getOutputDirectory()).toAbsolutePath().normalize();
        Path generatedDirectory = outputDirectory.resolve("irisscript/generated");
        Path launcherSource = generatedDirectory.resolve("IrisLauncher.java");
        if (!Files.isRegularFile(launcherSource)) {
            SourceSpan span = sourceManager.get(0).getSpan(0, 0);
            diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode("E3002"),
                    "Cannot run program: expected exactly one 'function main void()' or "
                            + "'global function main void()' entry point with no parameters",
                    span);
            return 1;
        }
        try {
            List<String> javaFiles;
            try (Stream<Path> paths = Files.walk(generatedDirectory)) {
                javaFiles = paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                        .map(Path::toString).sorted().toList();
            }
            if (javaFiles.isEmpty()) {
                throw new IOException("No generated Java source files were found");
            }
            Path javac = javaExecutable("javac");
            List<String> compileCommand = new ArrayList<>();
            compileCommand.add(javac.toString());
            compileCommand.add("-encoding");
            compileCommand.add("UTF-8");
            compileCommand.add("-d");
            compileCommand.add(outputDirectory.toString());
            compileCommand.addAll(javaFiles);
            Process compileProcess = new ProcessBuilder(compileCommand).inheritIO().start();
            int compileExit = compileProcess.waitFor();
            if (compileExit != 0) {
                return compileExit;
            }
            Path java = javaExecutable("java");
            List<String> runCommand = new ArrayList<>();
            runCommand.add(java.toString());
            runCommand.add("-cp");
            runCommand.add(outputDirectory.toString());
            runCommand.add(LAUNCHER_CLASS);
            runCommand.addAll(options.getProgramArguments());
            Process runProcess = new ProcessBuilder(runCommand).inheritIO().start();
            return runProcess.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode("E3003"),
                    "Program execution was interrupted.", sourceManager.get(0).getSpan(0, 0));
            return 130;
        } catch (IOException | RuntimeException exception) {
            diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode("E3004"),
                    "Failed to compile or execute generated Java: " + exception.getMessage(),
                    sourceManager.get(0).getSpan(0, 0));
            return 1;
        }
    }

    public static void help() {
        System.out.println("IrisScript compiler v1.0");
        System.out.println("Usage: irisscript <command> [options]");
        System.out.println("Commands:");
        System.out.println("  build  Build the project and compile source code to Java Source Code");
        System.out.println("  run    Build, compile generated Java, and execute the program");
        System.out.println("  help   Show this help message");
        System.out.println("Options:");
        System.out.println("  [-o / --output]    set a specified output directory");
        System.out.println("  [-nc / --no-color] report diagnostics without terminal color codes");
        System.out.println("  --                 pass remaining arguments to the IrisScript program");
    }

    private record AnalysisResult(List<CompilationUnit> units, SemanticModel semanticModel) {
    }

    private static AnalysisResult analyze(SourceManager sourceManager, DiagnosticsCollector diagnosticsCollector) {
        IrisLexer lexer = new IrisLexer(diagnosticsCollector);
        IrisParser parser = new IrisParser(diagnosticsCollector);
        IrisSemanticAnalyzer analyzer = new IrisSemanticAnalyzer(diagnosticsCollector);
        List<CompilationUnit> units = new ArrayList<>();
        for (int i = 0; i < sourceManager.getNumberOfFiles(); i++) {
            TokenStream tokens = lexer.tokenize(sourceManager.get(i));
            units.add(parser.parse(tokens));
        }
        SemanticModel semanticModel = analyzer.analyze(units);
        return new AnalysisResult(List.copyOf(units), semanticModel);
    }

    private static Path javaExecutable(String name) throws IOException {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
        Path path = Path.of(System.getProperty("java.home"), "bin", executable);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Java executable not found: " + path);
        }
        return path;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root))
            return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

}