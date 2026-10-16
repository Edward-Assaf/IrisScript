package irisscript.diagnostics.format;

import irisscript.diagnostics.Diagnostic;

public interface DiagnosticsFormatter {

    public abstract String format(Diagnostic diagnostic);

}