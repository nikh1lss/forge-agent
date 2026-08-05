package ns.forge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.*;

import ns.forge.tools.ForgeTool;
import ns.forge.tools.ToolRegistry;
import ns.forge.ui.ForgeEvent;
import ns.forge.ui.ForgeUi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class Harness {

    private final AnthropicClient client;
    private final Supplier<Optional<String>> userInput;
    private final HarnessConfig config;

    private final ToolRegistry tools;
    private final ForgeUi ui;

    public Harness(
            AnthropicClient client,
            Supplier<Optional<String>> userInput,
            HarnessConfig config,
            ToolRegistry tools,
            ForgeUi ui) {
        this.client = client;
        this.userInput = userInput;
        this.config = config;
        this.tools = tools;
        this.ui = ui;
    }

    public void run() {
        Conversation conversation = new Conversation();

        ui.emit(new ForgeEvent.Ready(System.getProperty("user.dir"), config.model().asString()));

        boolean readUser = true;

        while (true) {
            if (readUser) {
                ui.emit(new ForgeEvent.AwaitingInput());

                Optional<String> line = userInput.get();
                if (line.isEmpty()) {
                    break;
                }

                conversation.addUserText(line.get());
            }

            // Text and thinking already reached the front end as deltas while this was running;
            // the returned message is what gets appended to the history and mined for tool calls.
            Message response = runInferenceWithRetry(conversation);
            conversation.addAssistantResponse(response);

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
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

    private ContentBlockParam toolResult(String toolUseId, String content, boolean isError) {
        ui.emit(new ForgeEvent.ToolResult(toolUseId, content, isError));

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

    /** What started at a given block index, so its deltas and its stop can be interpreted. */
    private record OpenBlock(Kind kind, String toolId) {
        enum Kind {
            TEXT,
            THINKING,
            TOOL
        }
    }

    /**
     * Streams one response, relaying every event to the UI and accumulating the parts back into the
     * {@link Message} the conversation needs.
     *
     * <p>Deltas and stops carry a block index and nothing else, so the index-to-block map built at
     * {@code content_block_start} is what lets an {@code input_json_delta} be attributed to the
     * call it belongs to, and a {@code content_block_stop} know whether it is ending text,
     * thinking, or a tool call.
     */
    private Message streamOnce(MessageCreateParams params) {
        MessageAccumulator accumulator = MessageAccumulator.create();

        Map<Long, OpenBlock> open = new HashMap<>();

        ui.emit(new ForgeEvent.TurnStart());

        try (StreamResponse<RawMessageStreamEvent> stream =
                client.messages().createStreaming(params, RequestOptions.none())) {

            stream.stream()
                    .forEach(
                            event -> {
                                accumulator.accumulate(event);
                                relay(event, open);
                            });
        }

        Message message = accumulator.message();

        ui.emit(
                new ForgeEvent.TurnEnd(
                        message.stopReason().map(StopReason::asString).orElse("unknown"),
                        message.usage().inputTokens(),
                        message.usage().outputTokens()));

        return message;
    }

    private void relay(RawMessageStreamEvent event, Map<Long, OpenBlock> open) {
        event.contentBlockStart()
                .ifPresent(
                        start -> {
                            RawContentBlockStartEvent.ContentBlock block = start.contentBlock();

                            block.text()
                                    .ifPresent(
                                            ignored ->
                                                    open.put(
                                                            start.index(),
                                                            new OpenBlock(
                                                                    OpenBlock.Kind.TEXT, null)));

                            block.thinking()
                                    .ifPresent(
                                            ignored ->
                                                    open.put(
                                                            start.index(),
                                                            new OpenBlock(
                                                                    OpenBlock.Kind.THINKING, null)));

                            block.toolUse()
                                    .ifPresent(
                                            toolUse -> {
                                                open.put(
                                                        start.index(),
                                                        new OpenBlock(
                                                                OpenBlock.Kind.TOOL, toolUse.id()));
                                                ui.emit(
                                                        new ForgeEvent.ToolStart(
                                                                toolUse.id(), toolUse.name()));
                                            });
                        });

        event.contentBlockDelta()
                .ifPresent(
                        delta -> {
                            delta.delta()
                                    .text()
                                    .ifPresent(
                                            text ->
                                                    ui.emit(
                                                            new ForgeEvent.TextDelta(text.text())));

                            delta.delta()
                                    .thinking()
                                    .ifPresent(
                                            thinking ->
                                                    ui.emit(
                                                            new ForgeEvent.ThinkingDelta(
                                                                    thinking.thinking())));

                            delta.delta()
                                    .inputJson()
                                    .ifPresent(
                                            json -> {
                                                OpenBlock block = open.get(delta.index());
                                                if (block != null && block.toolId() != null) {
                                                    ui.emit(
                                                            new ForgeEvent.ToolInputDelta(
                                                                    block.toolId(),
                                                                    json.partialJson()));
                                                }
                                            });
                        });

        // A tool call gets no "end" of its own — the result event that follows the stream closes
        // it. Block kinds this doesn't know about (redacted thinking) were never opened, so they
        // fall through silently.
        event.contentBlockStop()
                .ifPresent(
                        stop -> {
                            OpenBlock block = open.remove(stop.index());
                            if (block == null) {
                                return;
                            }
                            switch (block.kind()) {
                                case TEXT -> ui.emit(new ForgeEvent.TextEnd());
                                case THINKING -> ui.emit(new ForgeEvent.ThinkingEnd());
                                case TOOL -> {}
                            }
                        });
    }

    /**
     * Calls the API, retrying on transient failures (overloaded, rate limit, network errors) with
     * exponential backoff. Non-retryable errors — like an invalid API key — are rethrown
     * immediately.
     *
     * <p>A retry re-emits {@link ForgeEvent.TurnStart}, which is the front end's cue to throw away
     * whatever the dead attempt had streamed so far.
     */
    private Message runInferenceWithRetry(Conversation conversation) {
        MessageCreateParams params = buildParams(conversation);

        long backoff = config.initialBackoffMillis();
        RuntimeException last = null;

        for (int attempt = 0; attempt <= config.maxRetries(); ++attempt) {
            try {
                return streamOnce(params);
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
            ui.emit(
                    new ForgeEvent.Notice(
                            "retry: transient API error, waiting " + backoff + "ms..."));
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
