package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record BinaryExpression(Expression left, BinaryOperator operator, Expression right, SourceSpan span)
        implements Expression {

    public BinaryExpression {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(right, "right must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}