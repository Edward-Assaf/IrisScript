package irisscript.ast.statements;

import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record WhileStatement(Expression condition, BlockStatement body, SourceSpan span) implements Statement {

    public WhileStatement {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}