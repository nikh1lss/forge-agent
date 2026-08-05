package ns.forge.ui;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The plain ANSI transcript: what forge printed before there was a TUI, now driven by streaming
 * events rather than whole blocks.
 *
 * <p>Text deltas are written as they arrive, so a block's "Forge: " prefix has to be printed lazily
 * on the first delta — the harness does not know a text block is coming until it starts. Tool calls
 * are the opposite: their inputs stream in, but a line-oriented transcript cannot show two
 * concurrent tool calls without interleaving them, so each call is buffered and printed whole when
 * it runs. That also keeps this identical to what forge printed before.
 *
 * <p>Thinking is dropped here. The TUI has somewhere to put it; a scrolling transcript does not.
 */
public final class ConsoleUi implements ForgeUi {

    private static final String ANSI_BLUE = "\u001b[94m";
    private static final String ANSI_YELLOW = "\u001b[93m";
    private static final String ANSI_GREEN = "\u001b[92m";
    private static final String ANSI_RED = "\u001b[91m";
    private static final String ANSI_DIM = "\u001b[2m";
    private static final String ANSI_RESET = "\u001b[0m";

    private final PrintStream out;

    /** Tool-use id to the call being assembled, in the order the model asked for them. */
    private final Map<String, PendingCall> pending = new LinkedHashMap<>();

    /** Whether the current text block has printed its "Forge: " prefix yet. */
    private boolean textOpen;

    public ConsoleUi(PrintStream out) {
        this.out = out;
    }

    private static final class PendingCall {
        final String name;
        final StringBuilder input = new StringBuilder();

        PendingCall(String name) {
            this.name = name;
        }
    }

    @Override
    public void emit(ForgeEvent event) {
        switch (event) {
            case ForgeEvent.Ready ignored -> out.println("Chat with forge (<C-c> to quit)\n");

            case ForgeEvent.AwaitingInput ignored -> {
                out.print(ANSI_BLUE + "You" + ANSI_RESET + ": ");
                out.flush();
            }

            // A retried attempt replays from the top, so anything half-written is stale.
            case ForgeEvent.TurnStart ignored -> {
                textOpen = false;
                pending.clear();
            }

            case ForgeEvent.TextDelta delta -> {
                if (!textOpen) {
                    out.print(ANSI_YELLOW + "\nForge" + ANSI_RESET + ": ");
                    textOpen = true;
                }
                out.print(delta.text());
                out.flush();
            }

            case ForgeEvent.TextEnd ignored -> {
                if (textOpen) {
                    out.println("\n");
                    textOpen = false;
                }
            }

            case ForgeEvent.ThinkingDelta ignored -> {}
            case ForgeEvent.ThinkingEnd ignored -> {}

            case ForgeEvent.ToolStart tool -> pending.put(tool.id(), new PendingCall(tool.name()));

            case ForgeEvent.ToolInputDelta delta -> {
                PendingCall call = pending.get(delta.id());
                if (call != null) {
                    call.input.append(delta.partialJson());
                }
            }

            case ForgeEvent.ToolResult result -> {
                PendingCall call = pending.remove(result.id());
                if (call != null) {
                    out.println(
                            ANSI_GREEN
                                    + "tool"
                                    + ANSI_RESET
                                    + ": "
                                    + call.name
                                    + "("
                                    + call.input
                                    + ")");
                }
                if (result.isError()) {
                    out.println(ANSI_RED + "  error" + ANSI_RESET + ": " + result.content());
                }
            }

            case ForgeEvent.TurnEnd ignored -> {}

            case ForgeEvent.Notice notice -> out.println(ANSI_DIM + notice.message() + ANSI_RESET);

            case ForgeEvent.Error error ->
                    out.println(ANSI_RED + "error" + ANSI_RESET + ": " + error.message());
        }
    }
}
