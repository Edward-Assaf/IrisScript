package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record ErrorExpression(SourceSpan span) implements Expression {

    public ErrorExpression {
        Objects.requireNonNull(span, "span must not be null");
    }

}