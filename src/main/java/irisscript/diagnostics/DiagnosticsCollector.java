package irisscript.diagnostics;

import java.util.ArrayList;
import java.util.List;

import irisscript.source.SourceSpan;

public class DiagnosticsCollector {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void report(DiagnosticSeverity severity, DiagnosticCode code, String message, SourceSpan span) {
        report(new Diagnostic(severity, code, message, span, List.of()));
    }

    public void report(DiagnosticSeverity severity, DiagnosticCode code, String message, SourceSpan span,
            List<DiagnosticLabel> labels) {
        report(new Diagnostic(severity, code, message, span, labels));
    }

    public List<Diagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    public int getDiagnosticsCount() {
        return diagnostics.size();
    }

    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

    public void clear() {
        diagnostics.clear();
    }

    public boolean hasErrors() {
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
                return true;
            }
        }
        return false;
    }

    public int getErrorCount() {
        return count(DiagnosticSeverity.ERROR);
    }

    public int getWarningCount() {
        return count(DiagnosticSeverity.WARNING);
    }

    public int getInfoCount() {
        return count(DiagnosticSeverity.INFO);
    }

    private int count(DiagnosticSeverity severity) {
        int count = 0;
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == severity) {
                count++;
            }
        }
        return count;
    }

}