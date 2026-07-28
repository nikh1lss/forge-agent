package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.Optional;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("API not found");
            System.exit(1);
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        Scanner scanner = new Scanner(System.in);

        Agent agent =
                new Agent(
                        client,
                        () ->
                                scanner.hasNextLine()
                                        ? Optional.of(scanner.nextLine())
                                        : Optional.empty(),
                        AgentConfig.defaults());
        agent.run();
        scanner.close();
    }
}
