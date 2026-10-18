package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record GroupingExpression(Expression expression, SourceSpan span) implements Expression {

    public GroupingExpression {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}