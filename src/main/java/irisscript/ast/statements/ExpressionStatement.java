package irisscript.ast.statements;

import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record ExpressionStatement(Expression expression, SourceSpan span) implements Statement {

    public ExpressionStatement {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}