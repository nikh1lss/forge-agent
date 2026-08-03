package ns.forge.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a shell command via {@code bash -c} and returns its exit code and combined output.
 *
 * <p><b>This tool executes arbitrary commands with the agent process's own privileges.</b> There is
 * no sandbox, no allowlist, and no confirmation prompt: whatever the model asks for, the shell runs.
 * Only point forge at directories you would be comfortable running a stranger's script in. The
 * timeout and output cap below bound how long a single command runs and how much it returns; they
 * are not a security boundary.
 *
 * <p>stdout and stderr are merged so the model sees them interleaved in the order they were
 * written, which is how a human reads a failing build. A non-zero exit is <em>not</em> treated as a
 * tool failure — it is reported as ordinary output, because "the tests failed" is information the
 * model needs to act on rather than an error in the tool itself.
 */
public final class BashTool extends AbstractTool<BashTool.Input> {
    private static final String NAME = "bash";

    static final long DEFAULT_TIMEOUT_MS = 30_000L;
    static final long MAX_TIMEOUT_MS = 600_000L;

    /** Cap on returned output, to keep one noisy command from swallowing the context window. */
    static final int MAX_OUTPUT_CHARS = 30_000;

    /** Grace period for the output reader to drain the pipe after the process exits. */
    private static final long DRAIN_TIMEOUT_MS = 2_000L;

    private final Path workingDir;

    public static class Input {
        public String command;
        public Long timeout_ms;
    }

    public BashTool() {
        this(Path.of("."));
    }

    /** Runs commands with {@code workingDir} as the process's working directory. */
    public BashTool(Path workingDir) {
        super(Input.class);
        this.workingDir = workingDir;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Tool spec() {
        return Tool.builder()
                .name(NAME)
                .description(
                        "Run a shell command with bash in the working directory and return its exit"
                            + " code together with its combined stdout and stderr. Use this to build"
                            + " and test your changes, to search the tree (grep, find), and to"
                            + " inspect state you cannot get from the other tools. A non-zero exit"
                            + " code is returned normally rather than as an error — read the output"
                            + " and decide what to do. The command runs without a shell session"
                            + " between calls, so cd does not persist; pass absolute paths or chain"
                            + " with &&. Do not run commands that wait for input, and prefer"
                            + " read_file over cat for reading a whole file.")
                .inputSchema(
                        Tool.InputSchema.builder()
                                .properties(
                                        Tool.InputSchema.Properties.builder()
                                                .putAdditionalProperty(
                                                        "command",
                                                        JsonValue.from(
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "The shell command to run,"
                                                                            + " e.g. './gradlew test"
                                                                            + " --console=plain'.")))
                                                .putAdditionalProperty(
                                                        "timeout_ms",
                                                        JsonValue.from(
                                                                Map.of(
                                                                        "type",
                                                                        "integer",
                                                                        "description",
                                                                        "Optional time to allow the"
                                                                            + " command before it is"
                                                                            + " killed, in"
                                                                            + " milliseconds."
                                                                            + " Defaults to "
                                                                                + DEFAULT_TIMEOUT_MS
                                                                                + ", capped at "
                                                                                + MAX_TIMEOUT_MS
                                                                                + ".")))
                                                .build())
                                .required(List.of("command"))
                                .build())
                .build();
    }

    @Override
    protected String run(Input input) throws IOException, InterruptedException {
        if (input.command == null || input.command.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        long timeoutMs = resolveTimeout(input.timeout_ms);

        Process process =
                new ProcessBuilder("bash", "-c", input.command)
                        .directory(workingDir.toFile())
                        .redirectErrorStream(true)
                        .start();

        // The pipe's buffer is finite: a command that writes more than it holds blocks forever
        // unless something is draining it, so the read has to happen off this thread. Draining
        // on a virtual thread also leaves waitFor free to enforce the timeout.
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread reader =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (InputStream out = process.getInputStream()) {
                                        out.transferTo(captured);
                                    } catch (IOException ignored) {
                                        // The pipe breaks when we forcibly kill a timed-out
                                        // process. Whatever was read before that is still worth
                                        // returning.
                                    }
                                });

        boolean exited = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
            process.waitFor();
        }
        reader.join(Duration.ofMillis(DRAIN_TIMEOUT_MS));

        String output = captured.toString(StandardCharsets.UTF_8);
        return format(output, process.exitValue(), exited, timeoutMs);
    }

    private static long resolveTimeout(Long requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.min(requested, MAX_TIMEOUT_MS);
    }

    private static String format(String output, int exitCode, boolean exited, long timeoutMs) {
        StringBuilder result = new StringBuilder();

        if (!exited) {
            result.append("command timed out after ")
                    .append(timeoutMs)
                    .append("ms and was killed\n");
        }
        result.append("exit code: ").append(exitCode).append('\n');

        String truncated = truncate(output);
        result.append(truncated.isBlank() ? "(no output)" : truncated);

        return result.toString();
    }

    /**
     * Drops the middle of oversized output rather than the tail. A build or test run puts the
     * summary that matters last, so head-only truncation would reliably hide the one thing the
     * model needs to read.
     */
    private static String truncate(String output) {
        if (output.length() <= MAX_OUTPUT_CHARS) {
            return output;
        }

        int half = MAX_OUTPUT_CHARS / 2;
        int dropped = output.length() - (half * 2);

        return output.substring(0, half)
                + "\n... ("
                + dropped
                + " characters truncated) ...\n"
                + output.substring(output.length() - half);
    }
}
