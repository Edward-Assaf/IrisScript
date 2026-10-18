package irisscript.ast.statements;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record ErrorStatement(SourceSpan span) implements Statement {

    public ErrorStatement {
        Objects.requireNonNull(span, "span must not be null");
    }

}