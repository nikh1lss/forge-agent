package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import io.github.cdimascio.dotenv.Dotenv;

import ns.forge.tools.ForgeTool;
import ns.forge.tools.ReadFileTool;
import ns.forge.tools.ToolRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Entry point for forge agent
 *
 * <p>Set the {@code ANTHROPIC_API_KEY} environment variable before running:
 *
 * <pre>{@code
 * ./gradlew run --console=plain
 * }</pre>
 */
public class Main {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("API not found");
            System.exit(1);
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        List<ForgeTool> tools = List.of(new ReadFileTool());

        ToolRegistry reg = new ToolRegistry(tools);

        Scanner scanner = new Scanner(System.in);

        Agent agent =
                new Agent(
                        client,
                        () ->
                                scanner.hasNextLine()
                                        ? Optional.of(scanner.nextLine())
                                        : Optional.empty(),
                        AgentConfig.defaults(),
                        reg);
        agent.run();
        scanner.close();
    }
}
