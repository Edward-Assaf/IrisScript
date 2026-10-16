package irisscript.diagnostics;

import java.util.List;
import java.util.Objects;

import irisscript.source.SourceSpan;

public final record Diagnostic(DiagnosticSeverity severity, DiagnosticCode code, String message,
        SourceSpan primarySpan, List<DiagnosticLabel> labels) {

    public Diagnostic {
        Objects.requireNonNull(severity, "diagnostic severity must not be null");
        Objects.requireNonNull(code, "diagnostic code must not be null");
        Objects.requireNonNull(message, "diagnostic message must not be null");
        Objects.requireNonNull(primarySpan, "diagnostic primary span must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Diagnostic message must not be blank");
        }
        if (labels != null) {
            labels = List.copyOf(labels);
            for (DiagnosticLabel label : labels) {
                Objects.requireNonNull(label, "diagnostic label must not be null");
            }
        }
    }

}