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
            int startIndex = this.tokens.getPosition();
            Token startToken = this.tokens.peek();
            boolean isDeclaration = (startToken.type() == TokenType.KEYWORD_FUNCTION)
                    || (startToken.type() == TokenType.KEYWORD_GLOBAL
                            && this.tokens.peek(1).type() == TokenType.KEYWORD_FUNCTION);
            try {
                if (this.tokens.match(TokenType.KEYWORD_GLOBAL)) {
                    Token globalToken = this.tokens.previous();
                    if (tokens.check(TokenType.KEYWORD_FUNCTION)) {
                        declarations.add(parseFunctionDeclaration(true, globalToken));
                    } else if (tokens.match(TokenType.KEYWORD_VARIABLE, TokenType.KEYWORD_CONSTANT)) {
                        boolean isConstant = tokens.previous().type() == TokenType.KEYWORD_CONSTANT;
                        statements.add(parseVariableOrConstantDeclaration(tokens.previous(), isConstant, true,
                                globalToken));
                    } else {
                        reportError("E1012", "Expected function or variable declaration after 'global'",
                                this.tokens.peek().span());
                        throw new ParseException();
                    }
                } else if (this.tokens.check(TokenType.KEYWORD_FUNCTION)) {
                    declarations.add(parseFunctionDeclaration(false, this.tokens.peek()));
                } else {
                    statements.add(parseStatement());
                }
            } catch (ParseException e) {
                SourceSpan errorSpan = spanFrom(startToken, lastConsumedOr(startToken, startIndex));
                if (isDeclaration) {
                    declarations.add(new ErrorDeclaration(errorSpan));
                } else {
                    statements.add(new ErrorStatement(errorSpan));
                }
                synchronizeTopLevel();
            }
            if (this.tokens.getPosition() == startIndex && !this.tokens.isAtEnd()) {
                this.tokens.consume();
            }
        }
        Token lastToken = this.tokens.peek();
        SourceSpan unitSpan = spanFrom(firstToken, lastToken);
        return new CompilationUnit(declarations, statements, unitSpan);
    }

    // ==========================================
    // Declarations
    // ==========================================

    private FunctionDeclaration parseFunctionDeclaration(boolean isGlobal, Token declarationStart) {
        tokens.consume(); // 'function'
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
        SourceSpan span = SourceSpan.covering(declarationStart.span(), body.span());
        return new FunctionDeclaration(nameToken.lexeme(), parameters, returnType, body, span, isGlobal);
    }

    // ==========================================
    // Statements
    // ==========================================

    private Statement parseStatement() {
        if (tokens.match(TokenType.KEYWORD_GLOBAL)) {
            Token globalToken = tokens.previous();
            reportError("E1013", "'global' declarations are only allowed at the top level", globalToken.span());
            throw new ParseException();
        }
        if (tokens.match(TokenType.KEYWORD_VARIABLE)) {
            return parseVariableOrConstantDeclaration(tokens.previous(), false, false, tokens.previous());
        }
        if (tokens.match(TokenType.KEYWORD_CONSTANT)) {
            return parseVariableOrConstantDeclaration(tokens.previous(), true, false, tokens.previous());
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
        if (tokens.match(TokenType.KEYWORD_TERMINAL)) {
            return parseTerminalStatement(tokens.previous());
        }
        if (tokens.check(TokenType.DELIMITER_LBRACE)) {
            return parseBlockStatement();
        }
        return parseExpressionStatement();
    }

    private VariableDeclarationStatement parseVariableOrConstantDeclaration(Token keywordToken, boolean isConstant,
            boolean isGlobal, Token declarationStart) {
        Token nameToken = consume(TokenType.IDENTIFIER, "E1009", "Expected variable name");
        TypeNode type = parseType();
        Expression initializer = null;
        if (tokens.match(TokenType.OPERATOR_ONE_EQ)) {
            initializer = parseExpression();
        } else if (isConstant) {
            reportError("E1010", "Constant '" + nameToken.lexeme() + "' must be initialized", nameToken.span());
        }
        Token semicolon = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after variable declaration");
        SourceSpan span = SourceSpan.covering(declarationStart.span(), semicolon.span());
        return new VariableDeclarationStatement(nameToken.lexeme(), type, initializer, isConstant, span, isGlobal);
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
        return new ReturnStatement(value, spanFrom(returnToken, semi));
    }

    private BreakStatement parseBreakStatement(Token breakToken) {
        Token semi = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after 'break'");
        return new BreakStatement(spanFrom(breakToken, semi));
    }

    private SkipStatement parseSkipStatement(Token skipToken) {
        Token semi = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after 'skip'");
        return new SkipStatement(spanFrom(skipToken, semi));
    }

    private TerminalStatement parseTerminalStatement(Token terminalToken) {
        Expression expression = parseExpression();
        Token semiColon = consume(TokenType.DELIMITER_SEMICOLON, "E1005", "Expected ';' after expression");
        SourceSpan span = spanFrom(terminalToken, semiColon);
        return new TerminalStatement(expression, span);
    }

    private BlockStatement parseBlockStatement() {
        Token openBrace = consume(TokenType.DELIMITER_LBRACE, "E1007", "Expected '{'");
        List<Statement> statements = new ArrayList<>();
        while (!tokens.check(TokenType.DELIMITER_RBRACE) && !tokens.isAtEnd()) {
            int startIndex = tokens.getPosition();
            Token startToken = tokens.peek();
            try {
                statements.add(parseStatement());
            } catch (ParseException e) {
                SourceSpan errorSpan = spanFrom(startToken, lastConsumedOr(startToken, startIndex));
                statements.add(new ErrorStatement(errorSpan));
                synchronizeStatement();
            }
            if (tokens.getPosition() == startIndex && !tokens.isAtEnd()
                    && !tokens.check(TokenType.DELIMITER_RBRACE)) {
                tokens.consume();
            }
        }
        Token closeBrace = consume(TokenType.DELIMITER_RBRACE, "E1006", "Expected '}' after block");
        return new BlockStatement(statements, spanFrom(openBrace, closeBrace));
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
        AssignmentOperator operator = null;
        if (tokens.match(TokenType.OPERATOR_ONE_EQ)) {
            operator = AssignmentOperator.ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_PLUS_EQ)) {
            operator = AssignmentOperator.ADD_ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_MINUS_EQ)) {
            operator = AssignmentOperator.SUBTRACT_ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_STAR_EQ)) {
            operator = AssignmentOperator.MULTIPLY_ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_SLASH_EQ)) {
            operator = AssignmentOperator.DIVIDE_ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_PERCENT_EQ)) {
            operator = AssignmentOperator.MODULO_ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_AMPERSAND_EQ)) {
            operator = AssignmentOperator.BITWISE_AND_ASSIGN;
        } else if (tokens.match(TokenType.OPERATOR_PIPE_EQ)) {
            operator = AssignmentOperator.BITWISE_OR_ASSIGN;
        }
        if (operator != null) {
            Token assignToken = tokens.previous();
            Expression value = parseAssignment();
            if (isAssignable(expr)) {
                SourceSpan span = SourceSpan.covering(expr.span(), value.span());
                return new AssignmentExpression(expr, value, operator, span);
            }
            reportError("E1004", "Invalid assignment target, only identifiers and index targets are permitted",
                    assignToken.span());
            return new ErrorExpression(SourceSpan.covering(expr.span(), value.span()));
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
        UnaryOperator operator = null;
        if (tokens.match(TokenType.OPERATOR_TWO_PLUS)) {
            operator = UnaryOperator.PRE_INCREMENT;
        } else if (tokens.match(TokenType.OPERATOR_TWO_MINUS)) {
            operator = UnaryOperator.PRE_DECREMENT;
        } else if (tokens.match(TokenType.OPERATOR_EXCLAMATION)) {
            operator = UnaryOperator.NOT;
        } else if (tokens.match(TokenType.OPERATOR_MINUS)) {
            operator = UnaryOperator.NEGATIVE;
        } else if (tokens.match(TokenType.OPERATOR_PLUS)) {
            operator = UnaryOperator.POSITIVE;
        }
        if (operator != null) {
            Token opToken = tokens.previous();
            Expression operand = parseUnary();
            return new UnaryExpression(operator, operand, SourceSpan.covering(opToken.span(), operand.span()));
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
            } else if (tokens.match(TokenType.DELIMITER_LBRACKET)) {
                Expression index = parseExpression();
                Token rbracket = consume(TokenType.DELIMITER_RBRACKET, "E1016", "Expected ']' after index");
                expr = new IndexExpression(expr, index, SourceSpan.covering(expr.span(), rbracket.span()));
            } else if (tokens.match(TokenType.OPERATOR_TWO_PLUS)) {
                Token opToken = tokens.previous();
                expr = new UnaryExpression(UnaryOperator.POST_INCREMENT, expr,
                        SourceSpan.covering(expr.span(), opToken.span()));
            } else if (tokens.match(TokenType.OPERATOR_TWO_MINUS)) {
                Token opToken = tokens.previous();
                expr = new UnaryExpression(UnaryOperator.POST_DECREMENT, expr,
                        SourceSpan.covering(expr.span(), opToken.span()));
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
            return new GroupingExpression(inner, spanFrom(lparen, rparen));
        }
        if (tokens.match(TokenType.DELIMITER_LBRACE)) {
            List<Expression> elements = new ArrayList<>();
            Token lbrace = tokens.previous();
            if (!tokens.check(TokenType.DELIMITER_RBRACE)) {
                do {
                    elements.add(parseExpression());
                } while (tokens.match(TokenType.DELIMITER_COMMA));
            }
            Token rbrace = consume(TokenType.DELIMITER_RBRACE, "E1015", "Expected '}' after array literal");
            return new ArrayLiteralExpression(elements, spanFrom(lbrace, rbrace));
        }
        Token current = tokens.peek();
        reportError("E1002", "Expected an expression, found '" + current.lexeme() + "'", current.span());
        if (!isExpressionBoundary(current.type()) && !tokens.isAtEnd()) {
            tokens.consume();
        }
        return new ErrorExpression(current.span());
    }

    // ==========================================
    // Types
    // ==========================================

    private TypeNode parseType() {
        Token token = tokens.peek();
        if (isTypeStart(token.type())) {
            SourceSpan span = token.span();
            tokens.consume();
            List<Expression> dimensions = new ArrayList<>();
            while (tokens.match(TokenType.DELIMITER_LBRACKET)) {
                Expression size = null;
                if (!tokens.check(TokenType.DELIMITER_RBRACKET)) {
                    size = parseExpression();
                }
                Token rbracket = consume(TokenType.DELIMITER_RBRACKET, "E1014", "Expected ']' for array declaration");
                dimensions.add(size);
                span = spanFrom(token, rbracket);
            }
            boolean isArray = !dimensions.isEmpty();
            return new TypeNode(token.lexeme(), isArray, dimensions, span);
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
        SourceSpan errorSpan = (code.equals("E1005") ? tokens.previous().span() : tokens.peek().span());
        reportError(code, message, errorSpan);
        throw new ParseException();
    }

    private void reportError(String code, String message, SourceSpan span) {
        diagnosticsCollector.report(DiagnosticSeverity.ERROR, new DiagnosticCode(code), message, span);
    }

    private boolean isExpressionBoundary(TokenType type) {
        return type == TokenType.DELIMITER_SEMICOLON
                || type == TokenType.DELIMITER_COMMA
                || type == TokenType.DELIMITER_RPAREN
                || type == TokenType.DELIMITER_RBRACE
                || type == TokenType.EOF;
    }

    private boolean isAssignable(Expression expression) {
        return expression instanceof IdentifierExpression || expression instanceof IndexExpression;
    }

    private Token lastConsumedOr(Token fallback, int startIndex) {
        if (tokens.getPosition() <= startIndex) {
            return fallback;
        }
        return tokens.previous();
    }

    private SourceSpan spanFrom(Token start, Token end) {
        if (start.span().sourceID() != end.span().sourceID()) {
            return start.span();
        }
        return SourceSpan.covering(start.span(), end.span());
    }

    private void synchronizeStatement() {
        while (!tokens.isAtEnd()) {
            if (tokens.check(TokenType.DELIMITER_RBRACE)) {
                return;
            }
            if (tokens.getPosition() > 0 && tokens.previous().type() == TokenType.DELIMITER_SEMICOLON) {
                return;
            }
            if (isStatementStart(tokens.peek().type())) {
                return;
            }
            tokens.consume();
            if (tokens.previous().type() == TokenType.DELIMITER_SEMICOLON) {
                return;
            }
        }
    }

    private boolean isStatementStart(TokenType type) {
        return switch (type) {
            case KEYWORD_IF, KEYWORD_WHILE, KEYWORD_RETURN, KEYWORD_BREAK, KEYWORD_SKIP, KEYWORD_VARIABLE,
                    KEYWORD_CONSTANT, DELIMITER_LBRACE, KEYWORD_GLOBAL, KEYWORD_TERMINAL ->
                true;
            default -> false;
        };
    }

    private void synchronizeTopLevel() {
        while (!tokens.isAtEnd()) {
            if (tokens.getPosition() > 0) {
                TokenType previousType = tokens.previous().type();
                if (previousType == TokenType.DELIMITER_SEMICOLON || previousType == TokenType.DELIMITER_RBRACE) {
                    return;
                }
            }
            TokenType type = tokens.peek().type();
            if (type == TokenType.KEYWORD_FUNCTION || isStatementStart(type)) {
                return;
            }
            tokens.consume();
        }
    }

    private static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

}