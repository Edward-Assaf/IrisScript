package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record TypeExpression(Expression expression, SourceSpan span) implements Expression {

    public TypeExpression {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}