package irisscript.ast.expressions;

import java.util.List;
import java.util.Objects;

import irisscript.source.SourceSpan;

public final record CallExpression(Expression callee, List<Expression> arguments, SourceSpan span)
        implements Expression {

    public CallExpression {
        Objects.requireNonNull(callee, "callee must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(span, "span must not be null");
        arguments = List.copyOf(arguments);
    }

}