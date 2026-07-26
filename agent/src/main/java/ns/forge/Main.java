package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("claude API not found");
            System.exit(1);
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        MessageCreateParams params =
                MessageCreateParams.builder()
                        .maxTokens(1024L)
                        .addUserMessage("Hello, Claude")
                        .model(Model.CLAUDE_HAIKU_4_5)
                        .build();

        Message message = client.messages().create(params);
        System.out.println(message.content());
    }
}
