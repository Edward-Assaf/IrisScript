package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record UnaryExpression(UnaryOperator operator, Expression operand, SourceSpan span) implements Expression {

    public UnaryExpression {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(operand, "operand must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}