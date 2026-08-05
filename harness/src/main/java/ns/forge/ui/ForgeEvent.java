package ns.forge.ui;

/**
 * Everything the harness tells its front end, as one closed set of cases.
 *
 * <p>This is the wire protocol. {@link JsonlUi} writes each case as a single JSON line on stdout
 * and the Ink TUI in {@code tui/} parses it back; {@code tui/src/protocol.ts} mirrors this file, so
 * the two have to be changed together. {@link ConsoleUi} renders the same events as the plain ANSI
 * transcript forge printed before there was a TUI.
 *
 * <p>The streaming cases arrive in a fixed shape per turn:
 *
 * <pre>
 * TurnStart
 *   (ThinkingDelta* ThinkingEnd | TextDelta* TextEnd | ToolStart ToolInputDelta*)*
 * TurnEnd
 * (ToolResult*)          one per ToolStart in the turn, in call order
 * </pre>
 *
 * <p>{@link TurnStart} is emitted once per <em>attempt</em>, not once per turn: a retried request
 * re-emits it, and a front end is expected to drop whatever partial block it was accumulating when
 * it sees one. That is what keeps a stream that dies mid-sentence from leaving half a word behind.
 */
public sealed interface ForgeEvent {

    /** The harness is up. Sent once, before the first prompt. */
    record Ready(String cwd, String model) implements ForgeEvent {}

    /** The loop wants a user message and is blocked until it gets one. */
    record AwaitingInput() implements ForgeEvent {}

    /** A request to the model is about to start. Discard any partial block on receipt. */
    record TurnStart() implements ForgeEvent {}

    record TextDelta(String text) implements ForgeEvent {}

    /** The current text block is complete and can be committed to scrollback. */
    record TextEnd() implements ForgeEvent {}

    record ThinkingDelta(String text) implements ForgeEvent {}

    record ThinkingEnd() implements ForgeEvent {}

    /** The model has committed to calling {@code name}; its input is still streaming. */
    record ToolStart(String id, String name) implements ForgeEvent {}

    /**
     * A fragment of the tool's input, as raw JSON text. Fragments for one {@code id} concatenate
     * into a complete JSON object; a tool called with no arguments may produce none at all.
     */
    record ToolInputDelta(String id, String partialJson) implements ForgeEvent {}

    /** A tool finished. {@code isError} means the tool threw, not that the command exited non-zero. */
    record ToolResult(String id, String content, boolean isError) implements ForgeEvent {}

    record TurnEnd(String stopReason, long inputTokens, long outputTokens) implements ForgeEvent {}

    /** Something worth showing that isn't part of the conversation, e.g. a retry. */
    record Notice(String message) implements ForgeEvent {}

    /** A failure that ended the run. */
    record Error(String message) implements ForgeEvent {}
}
