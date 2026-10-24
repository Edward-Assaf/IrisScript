package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record TernaryExpression(Expression condition, Expression ifTrue, Expression ifFalse, SourceSpan span)
        implements Expression {

    public TernaryExpression {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(ifTrue, "ifTrue must not be null");
        Objects.requireNonNull(ifFalse, "ifFalse must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}