package com.mychatgpt.event;

public class ConversationCompletedEvent {

    private final String sessionId;
    private final String userId;
    private final String userMessage;
    private final String assistantMessage;

    public ConversationCompletedEvent(String sessionId, String userId,
                                      String userMessage, String assistantMessage) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
    }

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getUserMessage() { return userMessage; }
    public String getAssistantMessage() { return assistantMessage; }
}