package ns.forge;

import com.anthropic.models.messages.Model;

/** Configuration for forge */
public record AgentConfig(
        Model model,
        long maxTokens,
        String systemPrompt,
        int maxRetries,
        long initialBackoffMillis) {

    private static final String DEFAULT_SYSTEM_PROMPT =
            """
            You are forge, a code-editing agent working in the user's current directory.

            Guidelines:
            - Read files before editing them so your edits match the actual content.
            - When editing, choose an old_str with enough surrounding context to be unique.
            - Prefer small, targeted edits over rewriting whole files.
            - After making changes, briefly summarize what you changed and why.
            - If a tool call fails, read the error, adjust, and try a different approach.
            """;

    public static AgentConfig defaults() {
        return new AgentConfig(Model.CLAUDE_HAIKU_4_5, 4096L, DEFAULT_SYSTEM_PROMPT, 3, 1_000L);
    }
}
