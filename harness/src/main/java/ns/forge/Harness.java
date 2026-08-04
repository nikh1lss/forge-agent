package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.*;

import ns.forge.tools.ForgeTool;
import ns.forge.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class Harness {

    // make text pretty
    private static final String ANSI_BLUE = "\u001b[94m";
    private static final String ANSI_YELLOW = "\u001b[93m";
    private static final String ANSI_RESET = "\u001b[0m";
    private static final String ANSI_GREEN = "\u001b[92m";

    private final AnthropicClient client;
    private final Supplier<Optional<String>> userInput;
    private final HarnessConfig config;

    private final ToolRegistry tools;

    public Harness(
            AnthropicClient client,
            Supplier<Optional<String>> userInput,
            HarnessConfig config,
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

            Message response = runInferenceWithRetry(conversation);
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

    private MessageCreateParams buildParams(Conversation conversation) {
        MessageCreateParams.Builder builder =
                MessageCreateParams.builder()
                        .model(config.model())
                        .maxTokens(config.maxTokens())
                        .system(config.systemPrompt())
                        .messages(conversation.messages());

        for (ForgeTool tool : tools.all()) {
            builder.addTool(tool.spec());
        }

        return builder.build();
    }

    /**
     * Calls the API, retrying on transient failures (overloaded, rate limit, network errors) with
     * exponential backoff. Non-retryable errors — like an invalid API key — are rethrown
     * immediately.
     */
    private Message runInferenceWithRetry(Conversation conversation) {
        MessageCreateParams params = buildParams(conversation);

        long backoff = config.initialBackoffMillis();
        RuntimeException last = null;

        for (int attempt = 0; attempt <= config.maxRetries(); ++attempt) {
            try {
                return client.messages().create(params, RequestOptions.none());
            } catch (AnthropicServiceException e) {
                if (!isRetryable(e.statusCode()) || attempt == config.maxRetries()) {
                    throw e;
                }

                last = e;
            } catch (AnthropicIoException e) {
                if (attempt == config.maxRetries()) {
                    throw e;
                }
                last = e;
            }
            System.out.println("retry: transient API error, waiting " + backoff + "ms...");
            sleep(backoff);
            backoff *= 2;
        }

        throw last;
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 529 || statusCode >= 500;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
