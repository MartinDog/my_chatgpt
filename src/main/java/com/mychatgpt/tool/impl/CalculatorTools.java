package com.mychatgpt.tool.impl;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTools {

    @Tool(description = "두 숫자의 사칙연산을 수행합니다. (더하기, 빼기, 곱하기, 나누기)")
    public String calculate(
            @ToolParam(description = "첫 번째 숫자") double a,
            @ToolParam(description = "두 번째 숫자") double b,
            @ToolParam(description = "연산 종류: add, subtract, multiply, divide") String operation) {
        try {
            double result = switch (operation) {
                case "add" -> a + b;
                case "subtract" -> a - b;
                case "multiply" -> a * b;
                case "divide" -> {
                    if (b == 0) throw new ArithmeticException("0으로 나눌 수 없습니다.");
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("Unknown operation: " + operation);
            };
            return String.format("%.2f %s %.2f = %.2f", a, operation, b, result);
        } catch (Exception e) {
            return "계산 오류: " + e.getMessage();
        }
    }
}
