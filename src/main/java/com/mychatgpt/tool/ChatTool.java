package com.mychatgpt.tool;

/**
 * Interface that all custom tools must implement.
 * Each tool provides its definition (name, description, parameters)
 * and an execute method.
 */
public interface ChatTool {

    /**
     * Returns the tool definition for OpenAI function calling.
     */
    ToolDefinition getDefinition();

    /**
     * Executes the tool with the given JSON arguments string.
     *
     * @param argumentsJson JSON string of the arguments
     * @return result string
     */
    String execute(String argumentsJson);
}
