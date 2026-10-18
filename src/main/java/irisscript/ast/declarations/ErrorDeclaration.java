package irisscript.ast.declarations;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record ErrorDeclaration(SourceSpan span) implements Declaration {

    public ErrorDeclaration {
        Objects.requireNonNull(span, "span must not be null");
    }

}