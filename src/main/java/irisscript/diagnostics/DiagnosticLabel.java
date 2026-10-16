package irisscript.diagnostics;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record DiagnosticLabel(SourceSpan span, String message) {

    public DiagnosticLabel {
        Objects.requireNonNull(span, "label span must not be null");
    }

    public DiagnosticLabel(SourceSpan span) {
        this(span, "");
    }

}