package irisscript.ast.expressions;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record IndexExpression(Expression target, Expression index, SourceSpan span)
        implements Expression {

    public IndexExpression {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}