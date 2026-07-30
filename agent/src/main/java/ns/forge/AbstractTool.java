package ns.forge;

import com.anthropic.models.messages.ToolUseBlock;

public abstract class AbstractTool<T> implements ForgeTool {
    private final Class<T> inputClass;

    protected AbstractTool(Class<T> inputClass) {
        this.inputClass = inputClass;
    }

    @Override
    public final Class<T> inputClass() {
        return inputClass;
    }

    @Override
    public final String execute(ToolUseBlock toolUse) throws Exception {
        T input = toolUse._input().convert(inputClass);
        if (input == null) {
            throw new IllegalArgumentException("failed to parse tool input");
        }
        return run(input);
    }

    protected abstract String run(T input) throws Exception;
}
