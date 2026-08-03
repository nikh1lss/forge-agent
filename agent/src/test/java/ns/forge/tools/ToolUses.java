package ns.forge.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.DirectCaller;
import com.anthropic.models.messages.ToolUseBlock;

import java.util.Map;

/**
 * Builds the {@link ToolUseBlock}s the model would send, so tools are exercised through their real
 * entry point ({@link ForgeTool#execute}) rather than by hand-constructing input POJOs.
 */
final class ToolUses {

    private ToolUses() {}

    static ToolUseBlock of(String name, Map<String, Object> input) {
        return ToolUseBlock.builder()
                .id("toolu_test")
                .name(name)
                .caller(DirectCaller.builder().build())
                .input(JsonValue.from(input))
                .build();
    }
}
