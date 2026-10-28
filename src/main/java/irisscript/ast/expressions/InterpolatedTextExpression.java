package irisscript.ast.expressions;

import java.util.List;
import java.util.Objects;

import irisscript.source.SourceSpan;

public final record InterpolatedTextExpression(List<Expression> parts, SourceSpan span) implements Expression {

    public InterpolatedTextExpression {
        Objects.requireNonNull(parts, "parts must not be null");
        Objects.requireNonNull(span, "span must not be null");
        parts = List.copyOf(parts);
    }

}