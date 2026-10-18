package irisscript.ast.statements;

import java.util.List;
import java.util.Objects;

import irisscript.source.SourceSpan;

public final record BlockStatement(List<Statement> statements, SourceSpan span) implements Statement {

    public BlockStatement {
        Objects.requireNonNull(statements, "statements must not be null");
        Objects.requireNonNull(span, "span must not be null");
        statements = List.copyOf(statements);
    }

}