package ns.forge;

import com.anthropic.models.messages.*;

import java.util.ArrayList;
import java.util.List;

/** The growing message history sent to the model on every turn. */
public final class Conversation {

    private final List<MessageParam> messages = new ArrayList<>();

    public List<MessageParam> messages() {
        return List.copyOf(messages);
    }

    public void addUserText(String text) {
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(text).build());
    }

    public void addToolResults(List<ContentBlockParam> toolResults) {
        messages.add(
                MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(toolResults)
                        .build());
    }

    /* Converts the model's response into an assistant turn and appends it */
    public void addAssistantResponse(Message response) {
        List<ContentBlockParam> blocks = new ArrayList<>();

        for (ContentBlock block : response.content()) {
            block.accept(
                    new ContentBlock.Visitor<Void>() {
                        @Override
                        public Void visitText(TextBlock text) {
                            blocks.add(
                                    ContentBlockParam.ofText(
                                            TextBlockParam.builder().text(text.text()).build()));
                            return null;
                        }

                        @Override
                        public Void visitToolUse(ToolUseBlock toolUse) {
                            blocks.add(
                                    ContentBlockParam.ofToolUse(
                                            ToolUseBlockParam.builder()
                                                    .id(toolUse.id())
                                                    .name(toolUse.name())
                                                    .input(toolUse._input())
                                                    .build()));
                            return null;
                        }

                        @Override
                        public Void visitWebSearchToolResult(WebSearchToolResultBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitServerToolUse(ServerToolUseBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitToolSearchToolResult(ToolSearchToolResultBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitCodeExecutionToolResult(CodeExecutionToolResultBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitWebFetchToolResult(WebFetchToolResultBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitContainerUpload(ContainerUploadBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitBashCodeExecutionToolResult(
                                BashCodeExecutionToolResultBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitTextEditorCodeExecutionToolResult(
                                TextEditorCodeExecutionToolResultBlock r) {
                            return null;
                        }

                        @Override
                        public Void visitThinking(ThinkingBlock thinking) {
                            blocks.add(
                                    ContentBlockParam.ofThinking(
                                            ThinkingBlockParam.builder()
                                                    .thinking(thinking.thinking())
                                                    .signature(thinking.signature())
                                                    .build()));
                            return null;
                        }

                        @Override
                        public Void visitRedactedThinking(RedactedThinkingBlock redacted) {
                            blocks.add(
                                    ContentBlockParam.ofRedactedThinking(
                                            RedactedThinkingBlockParam.builder()
                                                    .data(redacted.data())
                                                    .build()));
                            return null;
                        }
                    });

            block.text()
                    .ifPresent(
                            text ->
                                    blocks.add(
                                            ContentBlockParam.ofText(
                                                    TextBlockParam.builder()
                                                            .text(text.text())
                                                            .build())));
        }
        messages.add(
                MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .contentOfBlockParams(blocks)
                        .build());
    }
}
