package irisscript.ast;

import irisscript.source.SourceSpan;

public interface AstNode {

    public abstract SourceSpan span();

}