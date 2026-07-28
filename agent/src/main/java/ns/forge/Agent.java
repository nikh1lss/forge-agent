package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;

import java.util.Optional;
import java.util.function.Supplier;

public final class Agent {

    // make text pretty
    private static final String ANSI_BLUE = "\u001b[94m";
    private static final String ANSI_YELLOW = "\u001b[93m";
    private static final String ANSI_RESET = "\u001b[0m";

    private final AnthropicClient client;
    private final Supplier<Optional<String>> userInput;
    private final AgentConfig config;

    public Agent(AnthropicClient client, Supplier<Optional<String>> userInput, AgentConfig config) {
        this.client = client;
        this.userInput = userInput;
        this.config = config;
    }

    public void run() {
        Conversation conversation = new Conversation();

        System.out.println("Chat with forge (<C-c> to quit)\n");

        while (true) {
            System.out.print(ANSI_BLUE + "You" + ANSI_RESET + ": ");
            Optional<String> line = userInput.get();
            if (line.isEmpty()) {
                break;
            }

            conversation.addUserText(line.get());

            Message response = runInference(conversation);
            conversation.addAssistantResponse(response);

            for (ContentBlock block : response.content()) {
                block.text()
                        .ifPresent(
                                text ->
                                        System.out.println(
                                                ANSI_YELLOW
                                                        + "\nClaude"
                                                        + ANSI_RESET
                                                        + ": "
                                                        + text.text()
                                                        + "\n"));
            }
        }
    }

    private Message runInference(Conversation conversation) {
        return client.messages()
                .create(
                        MessageCreateParams.builder()
                                .model(config.model())
                                .maxTokens(config.maxTokens())
                                .system(config.systemPrompt())
                                .messages(conversation.messages())
                                .build());
    }
}
