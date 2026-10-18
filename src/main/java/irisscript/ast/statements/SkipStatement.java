package irisscript.ast.statements;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record SkipStatement(SourceSpan span) implements Statement {

    public SkipStatement {
        Objects.requireNonNull(span, "span must not be null");
    }

}