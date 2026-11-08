package irisscript.ast.statements;

import java.util.List;
import java.util.Objects;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

public final record ForStatement(List<Statement> initializations, Expression condition, List<Expression> updates,
        BlockStatement body, SourceSpan span) implements Statement {

    public ForStatement {
        Objects.requireNonNull(initializations, "initializations must not be null");
        Objects.requireNonNull(updates, "updates must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}