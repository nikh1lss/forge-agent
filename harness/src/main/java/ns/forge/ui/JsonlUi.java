package ns.forge.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.PrintStream;

/**
 * Writes {@link ForgeEvent}s as newline-delimited JSON, one object per line, for the Ink TUI to
 * read off the harness's stdout.
 *
 * <p>Field names follow the Anthropic API's snake_case so the two are read the same way. Every line
 * is flushed: the front end is a live UI, and a buffered delta is a delta that hasn't happened yet.
 *
 * <p>Nothing else may write to the stream this is given. {@code Main} hands it the real stdout and
 * points {@code System.out} at stderr for the duration, so a stray print cannot corrupt the
 * protocol.
 */
public final class JsonlUi implements ForgeUi {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PrintStream out;

    public JsonlUi(PrintStream out) {
        this.out = out;
    }

    @Override
    public void emit(ForgeEvent event) {
        ObjectNode node =
                switch (event) {
                    case ForgeEvent.Ready ready ->
                            obj("ready").put("cwd", ready.cwd()).put("model", ready.model());

                    case ForgeEvent.AwaitingInput ignored -> obj("awaiting_input");

                    case ForgeEvent.TurnStart ignored -> obj("turn_start");

                    case ForgeEvent.TextDelta delta -> obj("text_delta").put("text", delta.text());

                    case ForgeEvent.TextEnd ignored -> obj("text_end");

                    case ForgeEvent.ThinkingDelta delta ->
                            obj("thinking_delta").put("text", delta.text());

                    case ForgeEvent.ThinkingEnd ignored -> obj("thinking_end");

                    case ForgeEvent.ToolStart tool ->
                            obj("tool_start").put("id", tool.id()).put("name", tool.name());

                    case ForgeEvent.ToolInputDelta delta ->
                            obj("tool_input_delta")
                                    .put("id", delta.id())
                                    .put("partial_json", delta.partialJson());

                    case ForgeEvent.ToolResult result ->
                            obj("tool_result")
                                    .put("id", result.id())
                                    .put("content", result.content())
                                    .put("is_error", result.isError());

                    case ForgeEvent.TurnEnd turn ->
                            obj("turn_end")
                                    .put("stop_reason", turn.stopReason())
                                    .put("input_tokens", turn.inputTokens())
                                    .put("output_tokens", turn.outputTokens());

                    case ForgeEvent.Notice notice -> obj("notice").put("message", notice.message());

                    case ForgeEvent.Error error -> obj("error").put("message", error.message());
                };

        write(node);
    }

    private ObjectNode obj(String type) {
        return mapper.createObjectNode().put("type", type);
    }

    private void write(ObjectNode node) {
        try {
            // writeValueAsString cannot emit a raw newline inside a string, so one object really is
            // one line.
            out.println(mapper.writeValueAsString(node));
            out.flush();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialize event: " + node.get("type"), e);
        }
    }
}
