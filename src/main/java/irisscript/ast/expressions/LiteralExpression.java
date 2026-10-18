package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record LiteralExpression(Object value, SourceSpan span) implements Expression {

    public LiteralExpression {
        Objects.requireNonNull(span, "span must not be null");
    }

}