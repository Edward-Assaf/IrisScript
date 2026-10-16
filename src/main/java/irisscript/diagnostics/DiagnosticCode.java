package irisscript.diagnostics;

import java.util.Objects;

public final record DiagnosticCode(String value) {

    public DiagnosticCode {
        Objects.requireNonNull(value, "Code value must not be null");
        if (!value.matches("E[0-9]{4}")) {
            throw new IllegalArgumentException("Code value must be of the form E0000-E9999");
        }
    }

    @Override
    public String toString() {
        return value;
    }

}