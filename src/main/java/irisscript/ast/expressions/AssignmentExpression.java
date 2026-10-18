package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record AssignmentExpression(String name, Expression value, SourceSpan span) implements Expression {

    public AssignmentExpression {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}