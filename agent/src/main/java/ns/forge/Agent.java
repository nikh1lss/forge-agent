package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;

import ns.forge.tools.ForgeTool;
import ns.forge.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class Agent {

    // make text pretty
    private static final String ANSI_BLUE = "\u001b[94m";
    private static final String ANSI_YELLOW = "\u001b[93m";
    private static final String ANSI_RESET = "\u001b[0m";
    private static final String ANSI_GREEN = "\u001b[92m";

    private final AnthropicClient client;
    private final Supplier<Optional<String>> userInput;
    private final AgentConfig config;

    private final ToolRegistry tools;

    public Agent(
            AnthropicClient client,
            Supplier<Optional<String>> userInput,
            AgentConfig config,
            ToolRegistry tools) {
        this.client = client;
        this.userInput = userInput;
        this.config = config;
        this.tools = tools;
    }

    public void run() {
        Conversation conversation = new Conversation();

        System.out.println("Chat with forge (<C-c> to quit)\n");

        boolean readUser = true;

        while (true) {
            if (readUser) {
                System.out.print(ANSI_BLUE + "You" + ANSI_RESET + ": ");

                Optional<String> line = userInput.get();
                if (line.isEmpty()) {
                    break;
                }

                conversation.addUserText(line.get());
            }

            Message response = runInference(conversation);
            conversation.addAssistantResponse(response);

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.text()
                        .ifPresent(
                                text ->
                                        System.out.println(
                                                ANSI_YELLOW
                                                        + "\nForge"
                                                        + ANSI_RESET
                                                        + ": "
                                                        + text.text()
                                                        + "\n"));

                block.toolUse().ifPresent(toolUse -> toolResults.add(executeTool(toolUse)));
            }

            if (toolResults.isEmpty()) {
                readUser = true;
            } else {
                conversation.addToolResults(toolResults);
                readUser = false;
            }
        }
    }

    private Message runInference(Conversation conversation) {
        MessageCreateParams.Builder builder =
                MessageCreateParams.builder()
                        .model(config.model())
                        .maxTokens(config.maxTokens())
                        .system(config.systemPrompt())
                        .messages(conversation.messages());

        for (ForgeTool tool : tools.all()) {
            builder.addTool(tool.spec());
        }

        return client.messages().create(builder.build());
    }

    /** Looks up and executes a tool; failures become error tool-results. */
    private ContentBlockParam executeTool(ToolUseBlock toolUse) {
        System.out.println(
                ANSI_GREEN
                        + "tool"
                        + ANSI_RESET
                        + ": "
                        + toolUse.name()
                        + "("
                        + toolUse._input()
                        + ")");

        Optional<ForgeTool> tool = tools.find(toolUse.name());

        if (tool.isEmpty()) {
            return toolResult(toolUse.id(), "tool not found: " + toolUse.name(), true);
        }

        try {
            return toolResult(toolUse.id(), tool.get().execute(toolUse), false);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return toolResult(toolUse.id(), msg, true);
        }
    }

    private static ContentBlockParam toolResult(String toolUseId, String content, boolean isError) {
        return ContentBlockParam.ofToolResult(
                ToolResultBlockParam.builder()
                        .toolUseId(toolUseId)
                        .content(content)
                        .isError(isError)
                        .build());
    }
}
