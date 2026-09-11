package com.example.agentlearning.stage02;

import java.util.Map;

/**
 * 四则运算计算器（递归下降解析，无第三方库）。
 *
 * <p>支持：+ - * / 与括号，返回 double。
 * 由 {@code calculator} 工具调用，是"确定性留给程序"的一个例子——算数绝不交给模型。
 */
public final class Calculator {

    private Calculator() {
    }

    public static double eval(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        Parser parser = new Parser(expression);
        double value = parser.parseExpression();
        if (parser.pos < expression.length()) {
            throw new IllegalArgumentException("无法解析完整表达式: " + expression);
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        double parseExpression() {
            double value = parseTerm();
            while (peek() == '+' || peek() == '-') {
                char op = next();
                double right = parseTerm();
                value = op == '+' ? value + right : value - right;
            }
            return value;
        }

        double parseTerm() {
            double value = parseFactor();
            while (peek() == '*' || peek() == '/') {
                char op = next();
                double right = parseFactor();
                value = op == '*' ? value * right : value / right;
            }
            return value;
        }

        double parseFactor() {
            skipSpace();
            if (peek() == '(') {
                next();
                double value = parseExpression();
                skipSpace();
                if (peek() == ')') {
                    next();
                }
                return value;
            }
            return parseNumber();
        }

        double parseNumber() {
            skipSpace();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("无法解析数字: " + s.substring(pos));
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        char peek() {
            skipSpace();
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        char next() {
            return s.charAt(pos++);
        }

        void skipSpace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }
    }
}