package irisscript.diagnostics.format;

import java.nio.file.Paths;
import java.util.Objects;

import irisscript.diagnostics.Diagnostic;
import irisscript.diagnostics.DiagnosticLabel;
import irisscript.diagnostics.DiagnosticSeverity;
import irisscript.source.SourceFile;
import irisscript.source.SourceManager;
import irisscript.source.SourceSpan;

public final class ANSIFormatter implements DiagnosticsFormatter {

    private static final String RESET = "\u001B[0m";

    private static final String BOLD = "\u001B[1m";

    private static final String RED = "\u001B[31m";

    private static final String YELLOW = "\u001B[33m";

    private static final String BLUE = "\u001B[34m";

    private final SourceManager sourceManager;

    private final boolean useColor;

    public ANSIFormatter(SourceManager sourceManager, boolean useColor) {
        this.sourceManager = Objects.requireNonNull(sourceManager, "sourceManager must not be null");
        this.useColor = useColor;
    }

    @Override
    public String format(Diagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic must not be null");
        StringBuilder output = new StringBuilder();
        appendHeader(output, diagnostic);
        SourceSpan primarySpan = diagnostic.primarySpan();
        if (primarySpan != null) {
            appendSourceExcerpt(output, primarySpan, diagnostic.severity());
        }
        for (DiagnosticLabel label : diagnostic.labels()) {
            appendLabel(output, label, DiagnosticSeverity.INFO);
        }
        return output.toString();
    }

    private void appendHeader(StringBuilder output, Diagnostic diagnostic) {
        String severity = diagnostic.severity().name().toLowerCase();
        output.append(severityColor(diagnostic.severity()))
                .append(BOLD)
                .append(locationOf(diagnostic.primarySpan()))
                .append(": ")
                .append(severity)
                .append('[')
                .append(diagnostic.code())
                .append("]: ")
                .append(diagnostic.message())
                .append(System.lineSeparator())
                .append(RESET);
    }

    private void appendSourceExcerpt(StringBuilder output, SourceSpan span, DiagnosticSeverity severity) {
        SourceFile sourceFile = sourceManager.get(span.sourceID());
        if (sourceFile == null) {
            return;
        }
        int startOffset = span.startOffset();
        int endOffset = span.endOffset();
        int lineNumber = sourceFile.getLineNumber(startOffset);
        int lineStart = sourceFile.getLineOffsets()[lineNumber - 1];
        String lineText = sourceFile.getText(lineNumber);
        int lineEnd = lineStart + lineText.length();
        int underlineStart = startOffset - lineStart;
        int underlineEnd = Math.min(endOffset, lineEnd) - lineStart;
        underlineStart = Math.max(0, Math.min(underlineStart, lineText.length()));
        if (span.isEmpty()) {
            underlineEnd = underlineStart + 1;
        } else {
            underlineEnd = Math.max(underlineStart + 1, underlineEnd);
            underlineEnd = Math.min(underlineEnd, lineText.length());
        }
        String stringLineNumber = Integer.toString(lineNumber);
        output.append(severityColor(severity))
                .append(BOLD)
                .append(' ')
                .append(stringLineNumber)
                .append(" | ")
                .append(lineText)
                .append(System.lineSeparator())
                .append(" ".repeat(stringLineNumber.length() + 1))
                .append(" | ")
                .append(" ".repeat(underlineStart))
                .append("^".repeat(underlineEnd - underlineStart))
                .append(System.lineSeparator())
                .append(RESET);
    }

    private void appendLabel(StringBuilder output, DiagnosticLabel label, DiagnosticSeverity severity) {
        output.append(severityColor(severity)).append(BOLD).append("note: ");
        if (label.message() != null && !label.message().isBlank()) {
            output.append(label.message());
        }
        output.append(System.lineSeparator()).append(RESET);
        appendSourceExcerpt(output, label.span(), severity);
    }

    private String severityColor(DiagnosticSeverity severity) {
        if (!useColor) {
            return RESET;
        }
        return switch (severity) {
            case ERROR -> RED;
            case WARNING -> YELLOW;
            case INFO -> BLUE;
            default -> RESET;
        };
    }

    private String locationOf(SourceSpan span) {
        if (span == null) {
            return "<unknown>";
        }
        SourceFile sourceFile = sourceManager.get(span.sourceID());
        if (sourceFile == null) {
            return "<unknown>";
        }
        int offset = Math.min(span.startOffset(), sourceFile.getContentsLength());
        StringBuilder output = new StringBuilder();
        output.append(Paths.get(sourceFile.getName()))
                .append(':')
                .append(sourceFile.getLineNumber(offset))
                .append(':')
                .append(sourceFile.getColumnNumber(offset));
        return output.toString();
    }

}