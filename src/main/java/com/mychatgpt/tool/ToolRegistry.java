package com.mychatgpt.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ToolRegistry {

    private final Map<String, ChatTool> tools = new HashMap<>();

    public ToolRegistry(List<ChatTool> chatTools) {
        for (ChatTool tool : chatTools) {
            String name = tool.getDefinition().getName();
            tools.put(name, tool);
            log.info("Registered tool: {}", name);
        }
    }

    public List<ToolDefinition> getAllToolDefinitions() {
        return tools.values().stream()
                .map(ChatTool::getDefinition)
                .toList();
    }

    public String executeTool(String name, String argumentsJson) {
        ChatTool tool = tools.get(name);
        if (tool == null) {
            return "Error: Tool '" + name + "' not found.";
        }
        try {
            return tool.execute(argumentsJson);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", name, e);
            return "Error executing tool '" + name + "': " + e.getMessage();
        }
    }
}
