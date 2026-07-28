package ns.forge;

import com.anthropic.models.messages.*;

import java.util.ArrayList;
import java.util.List;

public final class Conversation {
    private final List<MessageParam> messages = new ArrayList<>();

    public List<MessageParam> messages() {
        return List.copyOf(messages);
    }

    public void addUserText(String text) {
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(text).build());
    }

    public void addAssistantResponse(Message response) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (ContentBlock block : response.content()) {
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
