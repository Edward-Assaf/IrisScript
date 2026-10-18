package irisscript.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import irisscript.ast.CompilationUnit;
import irisscript.ast.declarations.*;
import irisscript.ast.expressions.*;
import irisscript.ast.statements.*;
import irisscript.ast.types.TypeNode;
import irisscript.diagnostics.DiagnosticCode;
import irisscript.diagnostics.DiagnosticSeverity;
import irisscript.diagnostics.DiagnosticsCollector;
import irisscript.lexer.Token;
import irisscript.lexer.TokenStream;
import irisscript.lexer.TokenType;
import irisscript.source.SourceSpan;

public final class IrisParser {

    private final DiagnosticsCollector diagnosticsCollector;

    private TokenStream tokens;

    public IrisParser(DiagnosticsCollector diagnosticsCollector) {
        this.diagnosticsCollector = Objects.requireNonNull(diagnosticsCollector,
                "diagnosticsCollector must not be null");
    }

    public CompilationUnit parse(TokenStream tokens) {
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
        List<Declaration> declarations = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();
        Token firstToken = tokens.peek();
        while (!this.tokens.isAtEnd()) {
            try {
                if (this.tokens.check(TokenType.KEYWORD_FUNCTION)) {
                    declarations.add(parseFunctionDeclaration());
                } else {
                    statements.add(parseStatement());
                }
            } catch (ParseException e) {
                synchronize();
            }
        }
        Token lastToken = this.tokens.peek();
        SourceSpan unitSpan = SourceSpan.covering(firstToken.span(), lastToken.span());
        return new CompilationUnit(declarations, statements, unitSpan);
    }

    // ==========================================
    // Declarations
    // ==========================================

    private FunctionDeclaration parseFunctionDeclaration() {
        Token funcKeyword = tokens.consume(); // 'function'
        Token nameToken = consume(TokenType.IDENTIFIER, "E1009", "Expected function name");
        TypeNode returnType = parseType();
        consume(TokenType.DELIMITER_LPAREN, "E1008", "Expected '(' after function return type");
        List<ParameterDeclaration> parameters = new ArrayList<>();
        if (!tokens.check(TokenType.DELIMITER_RPAREN)) {
            do {
                if (!tokens.match(TokenType.KEYWORD_VARIABLE, TokenType.KEYWORD_CONSTANT)) {
                    reportError("E1011", "Expected 'variable' or 'constant' keyword", tokens.peek().span());
                    throw new ParseException();
                }
                Token keyword = tokens.previous();
                boolean isConstant = keyword.type() == TokenType.KEYWORD_CONSTANT;
                Token paramName = consume(TokenType.IDENTIFIER, "E1009", "Expected parameter name");
                TypeNode paramType = parseType();
                SourceSpan paramSpan = SourceSpan.covering(keyword.span(), paramType.span());
                parameters.add(new ParameterDeclaration(paramName.lexeme(), paramType, isConstant, paramSpan));
            } while (tokens.match(TokenType.DELIMITER_COMMA));
        }
        consume(TokenType.DELIMITER_RPAREN, "E1003", "Expected ')' after parameters");
        BlockStatement body = parseBlockStatement();
        SourceSpan span = SourceSpan.covering(funcKeyword.span(), body.span());
        return new FunctionDeclaration(nameToken.lexeme(), parameters, returnType, body, span);
    }

    // ==========================================
    // Statements
    // ==========================================

    private Statement parseStatement() {
        if (tokens.match(TokenType.KEYWORD_VARIABLE)) {
            return parseVariableOrConstantDeclaration(tokens.previous(), false);
        }
        if (tokens.match(TokenType.KEYWORD_CONSTANT)) {
            return parseVariableOrConstantDeclaration(tokens.previous(), true);
        }
        if (tokens.check(TokenType.KEYWORD_IF)) {
            return parseIfStatement();
        }
        if (tokens.check(TokenType.KEYWORD_WHILE)) {
            return parseWhileStatement();
        }
        if (tokens.match(TokenType.KEYWORD_RETURN)) {
            return parseReturnStatement(tokens.previous());
        }
        if (tokens.match(TokenType.KEYWORD_BREAK)) {
            return parseBreakStatement(tokens.previous());
        }
        if (tokens.match(TokenType.KEYWORD_SKIP)) {
            return parseSkipStatement(tokens.previous());
        }
        if (tokens.check(TokenType.DELIMITER_LBRACE)) {
            return parseBlockStatement();
        }
        return parseExpressionStatement();
    }

    private VariableDeclarationStatement parseVariableOrConstantDeclaration(Token keywordToken, boolean isConstant) {
        Token nameToken = consume(TokenType.IDENTIFIER, "E1009", "Expected variable name");
        TypeNode type = null;
        if (isTypeStart(tokens.peek().type())) {
            type = parseType();
        }
        Expression initializer = null;
        if (tokens.match(TokenType.OPERATOR_ONE_EQ)) {
            initializer = parseExpression();
        } else if (isConstant) {
            reportError("E1010", "Constant '" + nameToken.lexeme() + "' must be initialized", nameToken.span());
        }
        Token semicolon = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after variable declaration");
        SourceSpan span = SourceSpan.covering(keywordToken.span(), semicolon.span());
        return new VariableDeclarationStatement(nameToken.lexeme(), type, initializer, isConstant, span);
    }

    private IfStatement parseIfStatement() {
        Token ifKeyword = tokens.consume(); // 'if'
        consume(TokenType.DELIMITER_LPAREN, "E1008", "Expected '(' after 'if'");
        Expression condition = parseExpression();
        consume(TokenType.DELIMITER_RPAREN, "E1003", "Expected ')' after if condition");
        BlockStatement thenBranch = parseBlockStatement();
        Statement elseBranch = null;
        if (tokens.match(TokenType.KEYWORD_ELSEIF)) {
            elseBranch = parseElseIf(tokens.previous());
        } else if (tokens.match(TokenType.KEYWORD_ELSE)) {
            elseBranch = parseBlockStatement();
        }
        SourceSpan span = SourceSpan.covering(ifKeyword.span(),
                elseBranch != null ? elseBranch.span() : thenBranch.span());
        return new IfStatement(condition, thenBranch, elseBranch, span);
    }

    private IfStatement parseElseIf(Token elseIfKeyword) {
        consume(TokenType.DELIMITER_LPAREN, "E1008", "Expected '(' after 'elseif'");
        Expression condition = parseExpression();
        consume(TokenType.DELIMITER_RPAREN, "E1003", "Expected ')' after elseif condition");
        BlockStatement thenBranch = parseBlockStatement();
        Statement elseBranch = null;
        if (tokens.match(TokenType.KEYWORD_ELSEIF)) {
            elseBranch = parseElseIf(tokens.previous());
        } else if (tokens.match(TokenType.KEYWORD_ELSE)) {
            elseBranch = parseBlockStatement();
        }
        SourceSpan span = SourceSpan.covering(elseIfKeyword.span(),
                elseBranch != null ? elseBranch.span() : thenBranch.span());
        return new IfStatement(condition, thenBranch, elseBranch, span);
    }

    private WhileStatement parseWhileStatement() {
        Token whileKeyword = tokens.consume(); // 'while'
        consume(TokenType.DELIMITER_LPAREN, "E1008", "Expected '(' after 'while'");
        Expression condition = parseExpression();
        consume(TokenType.DELIMITER_RPAREN, "E1003", "Expected ')' after while condition");
        BlockStatement body = parseBlockStatement();
        SourceSpan span = SourceSpan.covering(whileKeyword.span(), body.span());
        return new WhileStatement(condition, body, span);
    }

    private ReturnStatement parseReturnStatement(Token returnToken) {
        Expression value = null;
        if (!tokens.check(TokenType.DELIMITER_SEMICOLON)) {
            value = parseExpression();
        }
        Token semi = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after return statement");
        return new ReturnStatement(value, SourceSpan.covering(returnToken.span(), semi.span()));
    }

    private BreakStatement parseBreakStatement(Token breakToken) {
        Token semi = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after 'break'");
        return new BreakStatement(SourceSpan.covering(breakToken.span(), semi.span()));
    }

    private SkipStatement parseSkipStatement(Token skipToken) {
        Token semi = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after 'skip'");
        return new SkipStatement(SourceSpan.covering(skipToken.span(), semi.span()));
    }

    private BlockStatement parseBlockStatement() {
        Token openBrace = consume(TokenType.DELIMITER_LBRACE, "E1007", "Expected '{'");
        List<Statement> statements = new ArrayList<>();
        while (!tokens.check(TokenType.DELIMITER_RBRACE) && !tokens.isAtEnd()) {
            statements.add(parseStatement());
        }
        Token closeBrace = consume(TokenType.DELIMITER_RBRACE, "E1006", "Expected '}' after block");
        return new BlockStatement(statements, SourceSpan.covering(openBrace.span(), closeBrace.span()));
    }

    private ExpressionStatement parseExpressionStatement() {
        Expression expr = parseExpression();
        Token semi = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after expression");
        return new ExpressionStatement(expr, SourceSpan.covering(expr.span(), semi.span()));
    }

    // ==========================================
    // Expressions
    // ==========================================

    private Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseLogicalOr();
        if (tokens.match(TokenType.OPERATOR_ONE_EQ)) {
            Token equalsToken = tokens.previous();
            Expression value = parseAssignment();
            if (expr instanceof IdentifierExpression id) {
                SourceSpan span = SourceSpan.covering(id.span(), value.span());
                return new AssignmentExpression(id.name(), value, span);
            }
            reportError("E1004", "Invalid assignment target, must be an identifier", equalsToken.span());
            throw new ParseException();
        }
        return expr;
    }

    private Expression parseLogicalOr() {
        Expression left = parseLogicalAnd();
        while (tokens.match(TokenType.OPERATOR_TWO_PIPE)) {
            Expression right = parseLogicalAnd();
            left = new BinaryExpression(left, BinaryOperator.OR, right,
                    SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseLogicalAnd() {
        Expression left = parseBitwiseOr();
        while (tokens.match(TokenType.OPERATOR_TWO_AMPERSAND)) {
            Expression right = parseBitwiseOr();
            left = new BinaryExpression(left, BinaryOperator.AND, right,
                    SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseBitwiseOr() {
        Expression left = parseBitwiseAnd();
        while (tokens.match(TokenType.OPERATOR_PIPE)) {
            Expression right = parseBitwiseAnd();
            left = new BinaryExpression(left, BinaryOperator.BITWISE_OR, right,
                    SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseBitwiseAnd() {
        Expression left = parseEquality();
        while (tokens.match(TokenType.OPERATOR_AMPERSAND)) {
            Expression right = parseEquality();
            left = new BinaryExpression(left, BinaryOperator.BITWISE_AND, right,
                    SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseEquality() {
        Expression left = parseRelational();
        while (true) {
            if (tokens.match(TokenType.OPERATOR_TWO_EQ)) {
                Expression right = parseRelational();
                left = new BinaryExpression(left, BinaryOperator.EQUAL, right,
                        SourceSpan.covering(left.span(), right.span()));
            } else if (tokens.check(TokenType.OPERATOR_EXCLAMATION)
                    && tokens.peek(1).type() == TokenType.OPERATOR_ONE_EQ) {
                tokens.consume(); // '!'
                tokens.consume(); // '='
                Expression right = parseRelational();
                left = new BinaryExpression(left, BinaryOperator.NOT_EQUAL, right,
                        SourceSpan.covering(left.span(), right.span()));
            } else {
                break;
            }
        }
        return left;
    }

    private Expression parseRelational() {
        Expression left = parseAdditive();
        while (true) {
            BinaryOperator op;
            if (tokens.match(TokenType.OPERATOR_LESS)) {
                op = BinaryOperator.LESS;
            } else if (tokens.match(TokenType.OPERATOR_LESS_EQ)) {
                op = BinaryOperator.LESS_EQ;
            } else if (tokens.match(TokenType.OPERATOR_GREATER)) {
                op = BinaryOperator.GREATER;
            } else if (tokens.match(TokenType.OPERATOR_GREATER_EQ)) {
                op = BinaryOperator.GREATER_EQ;
            } else {
                break;
            }
            Expression right = parseAdditive();
            left = new BinaryExpression(left, op, right, SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseAdditive() {
        Expression left = parseMultiplicative();
        while (true) {
            BinaryOperator op;
            if (tokens.match(TokenType.OPERATOR_PLUS)) {
                op = BinaryOperator.ADD;
            } else if (tokens.match(TokenType.OPERATOR_MINUS)) {
                op = BinaryOperator.SUBTRACT;
            } else {
                break;
            }
            Expression right = parseMultiplicative();
            left = new BinaryExpression(left, op, right, SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseMultiplicative() {
        Expression left = parseUnary();
        while (true) {
            BinaryOperator op;
            if (tokens.match(TokenType.OPERATOR_STAR)) {
                op = BinaryOperator.MULTIPLY;
            } else if (tokens.match(TokenType.OPERATOR_SLASH)) {
                op = BinaryOperator.DIVIDE;
            } else if (tokens.match(TokenType.OPERATOR_PERCENT)) {
                op = BinaryOperator.MODULO;
            } else {
                break;
            }
            Expression right = parseUnary();
            left = new BinaryExpression(left, op, right, SourceSpan.covering(left.span(), right.span()));
        }
        return left;
    }

    private Expression parseUnary() {
        if (tokens.match(TokenType.OPERATOR_EXCLAMATION)) {
            Token opToken = tokens.previous();
            Expression operand = parseUnary();
            return new UnaryExpression(UnaryOperator.NOT, operand,
                    SourceSpan.covering(opToken.span(), operand.span()));
        }
        if (tokens.match(TokenType.OPERATOR_MINUS)) {
            Token opToken = tokens.previous();
            Expression operand = parseUnary();
            return new UnaryExpression(UnaryOperator.NEGATIVE, operand,
                    SourceSpan.covering(opToken.span(), operand.span()));
        }
        if (tokens.match(TokenType.OPERATOR_PLUS)) {
            Token opToken = tokens.previous();
            Expression operand = parseUnary();
            return new UnaryExpression(UnaryOperator.POSITIVE, operand,
                    SourceSpan.covering(opToken.span(), operand.span()));
        }
        return parseCall();
    }

    private Expression parseCall() {
        Expression expr = parsePrimary();
        while (true) {
            if (tokens.match(TokenType.DELIMITER_LPAREN)) {
                List<Expression> arguments = new ArrayList<>();
                if (!tokens.check(TokenType.DELIMITER_RPAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (tokens.match(TokenType.DELIMITER_COMMA));
                }
                Token rparen = consume(TokenType.DELIMITER_RPAREN, "E1003", "Expected ')' after arguments");
                expr = new CallExpression(expr, arguments, SourceSpan.covering(expr.span(), rparen.span()));
            } else {
                break;
            }
        }
        return expr;
    }

    private Expression parsePrimary() {
        if (tokens.match(TokenType.LITERAL_INTEGER, TokenType.LITERAL_DECIMAL, TokenType.LITERAL_TEXT)) {
            Token token = tokens.previous();
            return new LiteralExpression(token.literal(), token.span());
        }
        if (tokens.match(TokenType.KEYWORD_TRUE)) {
            return new LiteralExpression(Boolean.TRUE, tokens.previous().span());
        }
        if (tokens.match(TokenType.KEYWORD_FALSE)) {
            return new LiteralExpression(Boolean.FALSE, tokens.previous().span());
        }
        if (tokens.match(TokenType.IDENTIFIER)) {
            Token token = tokens.previous();
            return new IdentifierExpression(token.lexeme(), token.span());
        }
        if (tokens.match(TokenType.DELIMITER_LPAREN)) {
            Token lparen = tokens.previous();
            Expression inner = parseExpression();
            Token rparen = consume(TokenType.DELIMITER_RPAREN, "E1003", "Expected ')' after expression");
            return new GroupingExpression(inner, SourceSpan.covering(lparen.span(), rparen.span()));
        }
        Token current = tokens.peek();
        reportError("E1002", "Unexpected token: '" + current.lexeme() + "'", current.span());
        throw new ParseException();
    }

    // ==========================================
    // Types
    // ==========================================

    private TypeNode parseType() {
        Token token = tokens.peek();
        if (isTypeStart(token.type())) {
            tokens.consume();
            return new TypeNode(token.lexeme(), token.span());
        }
        reportError("E1001", "Expected type name, found '" + token.lexeme() + "'", token.span());
        throw new ParseException();
    }

    private boolean isTypeStart(TokenType type) {
        return type == TokenType.KEYWORD_INTEGER
                || type == TokenType.KEYWORD_DECIMAL
                || type == TokenType.KEYWORD_TEXT
                || type == TokenType.KEYWORD_BOOLEAN
                || type == TokenType.KEYWORD_VOID
                || type == TokenType.IDENTIFIER;
    }

    // ==========================================
    // Helper & Error Recovery Methods
    // ==========================================

    private Token consume(TokenType expected, String code, String message) {
        if (tokens.check(expected)) {
            return tokens.consume();
        }
        reportError(code, message, (code == "E1005") ? tokens.previous().span() : tokens.peek().span());
        throw new ParseException();
    }

    private void reportError(String code, String message, SourceSpan span) {
        diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode(code), message, span);
    }

    private void synchronize() {
        if (!tokens.isAtEnd()) {
            tokens.consume();
        }
        while (!tokens.isAtEnd()) {
            if (tokens.previous().type() == TokenType.DELIMITER_SEMICOLON) {
                return;
            }
            if (tokens.previous().type() == TokenType.DELIMITER_RBRACE) {
                return;
            }
            switch (tokens.peek().type()) {
                case KEYWORD_FUNCTION:
                case KEYWORD_IF:
                case KEYWORD_WHILE:
                case KEYWORD_RETURN:
                case KEYWORD_BREAK:
                case KEYWORD_SKIP:
                case KEYWORD_VARIABLE:
                case KEYWORD_CONSTANT:
                case KEYWORD_ELSE:
                case KEYWORD_ELSEIF:
                    return;
                default:
                    tokens.consume();
            }
        }
    }

    private static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

}