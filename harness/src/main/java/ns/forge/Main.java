package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

import ns.forge.tools.BashTool;
import ns.forge.tools.EditFileTool;
import ns.forge.tools.ForgeTool;
import ns.forge.tools.ListFilesTool;
import ns.forge.tools.ReadFileTool;
import ns.forge.tools.ToolRegistry;
import ns.forge.ui.ConsoleUi;
import ns.forge.ui.ForgeEvent;
import ns.forge.ui.ForgeUi;
import ns.forge.ui.JsonlUi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Supplier;

/**
 * Entry point for forge harness.
 *
 * <p>Set the {@code ANTHROPIC_API_KEY} environment variable before running:
 *
 * <pre>{@code
 * ./gradlew run --console=plain              # ANSI transcript
 * ./gradlew run --args=--jsonl               # JSON lines, for the Ink TUI in tui/
 * }</pre>
 */
public class Main {

    public static void main(String[] args) {
        boolean jsonl = List.of(args).contains("--jsonl");

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("ANTHROPIC_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("API not found");
            System.exit(1);
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        List<ForgeTool> tools =
                List.of(new ReadFileTool(), new ListFilesTool(), new EditFileTool(), new BashTool());
        ToolRegistry reg = new ToolRegistry(tools);

        // Claim stdout before anything else can print to it. In JSONL mode the stream is the
        // protocol, so System.out is repointed at stderr: a stray println then shows up in the
        // TUI's log instead of corrupting a frame.
        PrintStream stdout = System.out;
        ForgeUi ui;
        Supplier<Optional<String>> input;
        Runnable cleanup;

        if (jsonl) {
            System.setOut(System.err);
            ui = new JsonlUi(stdout);
            input = jsonlInput();
            cleanup = () -> {};
        } else {
            Scanner scanner = new Scanner(System.in);
            ui = new ConsoleUi(stdout);
            input = () -> scanner.hasNextLine() ? Optional.of(scanner.nextLine()) : Optional.empty();
            cleanup = scanner::close;
        }

        Harness harness = new Harness(client, input, HarnessConfig.defaults(), reg, ui);

        try {
            harness.run();
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            ui.emit(new ForgeEvent.Error(msg));
            System.exit(1);
        } finally {
            cleanup.run();
        }
    }

    /**
     * Reads user turns as JSON lines: {@code {"type":"user","text":"..."}}.
     *
     * <p>Going through JSON rather than reading raw lines is what lets a message contain newlines —
     * the TUI's input box accepts them, and a line-oriented protocol could not carry them. Lines
     * that aren't a user turn are skipped rather than treated as EOF, so a future control message
     * doesn't end the session on an older harness.
     */
    private static Supplier<Optional<String>> jsonlInput() {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        ObjectMapper mapper = new ObjectMapper();

        return () -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    JsonNode node = mapper.readTree(line);
                    JsonNode text = node.path("text");

                    if ("user".equals(node.path("type").asText()) && text.isTextual()) {
                        return Optional.of(text.asText());
                    }
                }
                return Optional.empty();
            } catch (IOException e) {
                return Optional.empty();
            }
        };
    }
}
