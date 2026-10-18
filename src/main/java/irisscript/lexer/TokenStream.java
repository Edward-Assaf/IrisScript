package irisscript.lexer;

import java.util.List;
import java.util.Objects;

public final class TokenStream {

    private final Token[] tokens;

    private int cursor = 0;

    public TokenStream(List<Token> tokens) {
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("tokens list must not be empty");
        }
        this.tokens = tokens.toArray(new Token[0]);
    }

    public TokenStream(Token[] tokens) {
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (tokens.length == 0) {
            throw new IllegalArgumentException("tokens array must not be empty");
        }
        this.tokens = tokens.clone();
    }

    public Token peek() {
        if (cursor >= tokens.length) {
            return tokens[tokens.length - 1];
        }
        return tokens[cursor];
    }

    public Token peek(int offset) {
        int target = cursor + offset;
        if (target < 0) {
            throw new IndexOutOfBoundsException("Cannot peek before the start of the token stream: " + target);
        }
        if (target >= tokens.length) {
            return tokens[tokens.length - 1];
        }
        return tokens[target];
    }

    public Token consume() {
        Token current = peek();
        if (!isAtEnd()) {
            cursor++;
        }
        return current;
    }

    public Token previous() {
        if (cursor == 0) {
            throw new IllegalStateException("Cannot retrieve previous token at the start of the stream");
        }
        return tokens[cursor - 1];
    }

    public boolean check(TokenType type) {
        if (isAtEnd()) {
            return type == TokenType.EOF;
        }
        return peek().type() == type;
    }

    public boolean match(TokenType type) {
        if (check(type)) {
            consume();
            return true;
        }
        return false;
    }

    public boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                consume();
                return true;
            }
        }
        return false;
    }

    public boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    public int getPosition() {
        return cursor;
    }

    public void seek(int position) {
        if (position < 0 || position >= tokens.length) {
            throw new IndexOutOfBoundsException(
                    "Position " + position + " is out of bounds for token count " + tokens.length);
        }
        this.cursor = position;
    }

    public int size() {
        return tokens.length;
    }

}