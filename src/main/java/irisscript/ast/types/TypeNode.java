package irisscript.ast.types;

import java.util.Objects;

import irisscript.ast.AstNode;
import irisscript.source.SourceSpan;

public final record TypeNode(String name, SourceSpan span) implements AstNode {

    public TypeNode {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

}