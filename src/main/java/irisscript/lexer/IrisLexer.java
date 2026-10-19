package irisscript.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import irisscript.diagnostics.DiagnosticCode;
import irisscript.diagnostics.DiagnosticSeverity;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.source.SourceFile;

public final class IrisLexer {

    private final DiagnosticsCollector diagnosticsCollector;

    private SourceFile sourceFile;

    private String contents;

    private int start;

    private int current;

    private List<Token> tokens;

    public IrisLexer(DiagnosticsCollector diagnosticsCollector) {
        this.diagnosticsCollector = Objects.requireNonNull(diagnosticsCollector,
                "diagnosticsCollector must not be null");
    }

    public TokenStream tokenize(SourceFile sourceFile) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        this.contents = sourceFile.getContents();
        this.start = 0;
        this.current = 0;
        this.tokens = new ArrayList<>();
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", sourceFile.getSpan(current, current)));
        return new TokenStream(tokens);
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ', '\r', '\t', '\n':
                break;
            case '(':
                addToken(TokenType.DELIMITER_LPAREN);
                break;
            case ')':
                addToken(TokenType.DELIMITER_RPAREN);
                break;
            case '{':
                addToken(TokenType.DELIMITER_LBRACE);
                break;
            case '}':
                addToken(TokenType.DELIMITER_RBRACE);
                break;
            case '[':
                addToken(TokenType.DELIMITER_LBRACKET);
                break;
            case ']':
                addToken(TokenType.DELIMITER_RBRACKET);
                break;
            case ',':
                addToken(TokenType.DELIMITER_COMMA);
                break;
            case '.':
                addToken(TokenType.DELIMITER_DOT);
                break;
            case ':':
                addToken(TokenType.DELIMITER_COLON);
                break;
            case ';':
                addToken(TokenType.DELIMITER_SEMICOLON);
                break;
            case '+':
                addToken(TokenType.OPERATOR_PLUS);
                break;
            case '-':
                addToken(match('>') ? TokenType.DELIMITER_ARROW : TokenType.OPERATOR_MINUS);
                break;
            case '*':
                addToken(TokenType.OPERATOR_STAR);
                break;
            case '%':
                addToken(TokenType.OPERATOR_PERCENT);
                break;
            case '=':
                addToken(match('=') ? TokenType.OPERATOR_TWO_EQ : TokenType.OPERATOR_ONE_EQ);
                break;
            case '!':
                addToken(TokenType.OPERATOR_EXCLAMATION);
                break;
            case '<':
                addToken(match('=') ? TokenType.OPERATOR_LESS_EQ : TokenType.OPERATOR_LESS);
                break;
            case '>':
                addToken(match('=') ? TokenType.OPERATOR_GREATER_EQ : TokenType.OPERATOR_GREATER);
                break;
            case '&':
                addToken(match('&') ? TokenType.OPERATOR_TWO_AMPERSAND : TokenType.OPERATOR_AMPERSAND);
                break;
            case '|':
                addToken(match('|') ? TokenType.OPERATOR_TWO_PIPE : TokenType.OPERATOR_PIPE);
                break;
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                } else if (match('*')) {
                    scanBlockComment();
                } else {
                    addToken(TokenType.OPERATOR_SLASH);
                }
                break;
            case '"':
                scanText();
                break;
            default:
                if (isDigit(c)) {
                    scanNumber();
                } else if (isAlpha(c)) {
                    scanIdentifierOrKeyword();
                } else {
                    reportError("E0006", "Unexpected character: '" + c + "'", start, current);
                }
                break;
        }
    }

    private void scanText() {
        StringBuilder value = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            char c = advance();
            if (c == '\\') {
                if (isAtEnd()) {
                    break;
                }
                char escape = advance();
                switch (escape) {
                    case 'n':
                        value.append('\n');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case '"':
                        value.append('"');
                        break;
                    case '\\':
                        value.append('\\');
                        break;
                    default:
                        reportError("E0005", "Invalid escape sequence: '\\" + escape + "'", current - 2, current);
                        value.append(escape);
                        break;
                }
            } else {
                value.append(c);
            }
        }
        if (isAtEnd()) {
            reportError("E0004", "Unterminated text literal", start, current);
            return;
        }
        advance(); // Consume the closing "
        addToken(TokenType.LITERAL_TEXT, value.toString());
    }

    private void scanNumber() {
        while (isDigit(peek())) {
            advance();
        }
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // Consume the '.'
            while (isDigit(peek())) {
                advance();
            }
            String text = contents.substring(start, current);
            try {
                double value = Double.parseDouble(text);
                addToken(TokenType.LITERAL_DECIMAL, value);
            } catch (NumberFormatException e) {
                reportError("E0002", "Invalid decimal literal: " + text, start, current);
            }
        } else {
            String text = contents.substring(start, current);
            try {
                long value = Long.parseLong(text);
                addToken(TokenType.LITERAL_INTEGER, value);
            } catch (NumberFormatException e) {
                reportError("E0003", "Integer literal too large: " + text, start, current);
            }
        }
    }

    private void scanIdentifierOrKeyword() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = contents.substring(start, current);
        TokenType type = KEYWORDS.get(text);
        if (type == null) {
            addToken(TokenType.IDENTIFIER);
        } else if (type == TokenType.KEYWORD_TRUE) {
            addToken(TokenType.KEYWORD_TRUE, Boolean.TRUE);
        } else if (type == TokenType.KEYWORD_FALSE) {
            addToken(TokenType.KEYWORD_FALSE, Boolean.FALSE);
        } else {
            addToken(type);
        }
    }

    private void scanBlockComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance(); // consume '*'
                advance(); // consume '/'
                return;
            }
            advance();
        }
        reportError("E0001", "Unterminated block comment", start, current);
    }

    private boolean match(char expected) {
        if (isAtEnd() || contents.charAt(current) != expected) {
            return false;
        }
        current++;
        return true;
    }

    private char advance() {
        return contents.charAt(current++);
    }

    private char peek() {
        return isAtEnd() ? '\0' : contents.charAt(current);
    }

    private char peekNext() {
        return (current + 1 >= sourceFile.getContentsLength()) ? '\0' : contents.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= sourceFile.getContentsLength();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String lexeme = contents.substring(start, current);
        tokens.add(new Token(type, lexeme, literal, sourceFile.getSpan(start, current)));
    }

    private void reportError(String code, String message, int startOffset, int endOffset) {
        diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode(code), message,
                sourceFile.getSpan(startOffset, endOffset));
    }

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("variable", TokenType.KEYWORD_VARIABLE),
            Map.entry("true", TokenType.KEYWORD_TRUE),
            Map.entry("false", TokenType.KEYWORD_FALSE),
            Map.entry("integer", TokenType.KEYWORD_INTEGER),
            Map.entry("decimal", TokenType.KEYWORD_DECIMAL),
            Map.entry("text", TokenType.KEYWORD_TEXT),
            Map.entry("boolean", TokenType.KEYWORD_BOOLEAN),
            Map.entry("if", TokenType.KEYWORD_IF),
            Map.entry("elseif", TokenType.KEYWORD_ELSEIF),
            Map.entry("else", TokenType.KEYWORD_ELSE),
            Map.entry("while", TokenType.KEYWORD_WHILE),
            Map.entry("break", TokenType.KEYWORD_BREAK),
            Map.entry("skip", TokenType.KEYWORD_SKIP),
            Map.entry("function", TokenType.KEYWORD_FUNCTION),
            Map.entry("return", TokenType.KEYWORD_RETURN),
            Map.entry("void", TokenType.KEYWORD_VOID),
            Map.entry("constant", TokenType.KEYWORD_CONSTANT),
            Map.entry("global", TokenType.KEYWORD_GLOBAL));

}