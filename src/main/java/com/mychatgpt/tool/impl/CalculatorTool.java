package com.mychatgpt.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychatgpt.tool.ChatTool;
import com.mychatgpt.tool.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CalculatorTool implements ChatTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "calculator",
                "두 숫자의 사칙연산을 수행합니다. (더하기, 빼기, 곱하기, 나누기)",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("type", "number", "description", "첫 번째 숫자"),
                                "b", Map.of("type", "number", "description", "두 번째 숫자"),
                                "operation", Map.of(
                                        "type", "string",
                                        "description", "연산 종류: add, subtract, multiply, divide",
                                        "enum", List.of("add", "subtract", "multiply", "divide")
                                )
                        ),
                        "required", List.of("a", "b", "operation")
                )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            double a = args.path("a").asDouble();
            double b = args.path("b").asDouble();
            String op = args.path("operation").asText();

            double result = switch (op) {
                case "add" -> a + b;
                case "subtract" -> a - b;
                case "multiply" -> a * b;
                case "divide" -> {
                    if (b == 0) throw new ArithmeticException("0으로 나눌 수 없습니다.");
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("Unknown operation: " + op);
            };

            return String.format("%.2f %s %.2f = %.2f", a, op, b, result);
        } catch (Exception e) {
            return "계산 오류: " + e.getMessage();
        }
    }
}
