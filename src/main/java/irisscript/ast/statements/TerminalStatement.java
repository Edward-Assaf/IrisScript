package irisscript.ast.statements;

import irisscript.ast.expressions.Expression;
import irisscript.source.SourceSpan;

import java.util.Objects;

public final record TerminalStatement(Expression expression, SourceSpan span) implements Statement {

    public TerminalStatement {
        Objects.requireNonNull(expression, "expression must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}