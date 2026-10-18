package irisscript.ast;

import java.util.List;
import java.util.Objects;

import irisscript.ast.declarations.Declaration;
import irisscript.ast.statements.Statement;
import irisscript.source.SourceSpan;

public final record CompilationUnit(List<Declaration> declarations, List<Statement> statements, SourceSpan span)
        implements AstNode {

    public CompilationUnit {
        Objects.requireNonNull(declarations, "declarations must not be null");
        Objects.requireNonNull(statements, "statements must not be null");
        Objects.requireNonNull(span, "span must not be null");
        declarations = List.copyOf(declarations);
        statements = List.copyOf(statements);
    }

}