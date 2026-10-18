package irisscript.ast.statements;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record BreakStatement(SourceSpan span) implements Statement {

    public BreakStatement {
        Objects.requireNonNull(span, "span must not be null");
    }

}