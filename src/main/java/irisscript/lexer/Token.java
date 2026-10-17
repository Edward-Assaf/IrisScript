package irisscript.lexer;

import java.util.Objects;

import irisscript.source.SourceSpan;

public final record Token(TokenType type, String lexeme, Object literal, SourceSpan span) {

    public Token {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(lexeme, "lexeme must not be null");
        Objects.requireNonNull(span, "span must not be null");
    }

    public Token(TokenType type, String lexeme, SourceSpan span) {
        this(type, lexeme, null, span);
    }

}