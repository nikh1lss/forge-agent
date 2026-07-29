package ns.forge;

import com.anthropic.models.messages.ToolUseBlock;

public interface Tool {
    String name();

    Class<?> inputClass();

    String execute(ToolUseBlock toolUse) throws Exception;
}
