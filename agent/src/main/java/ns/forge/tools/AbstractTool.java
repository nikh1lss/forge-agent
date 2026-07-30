package ns.forge.tools;

import com.anthropic.models.messages.ToolUseBlock;

/**
 * Base class for tools with a typed input.
 *
 * <p>Handles the JSON-to-POJO parsing of the model's tool input so that concrete tools only
 * implement {@link #run(Object)} against their own input type. Generics never leak past this class.
 *
 * @param <T> the annotated input POJO for this tool
 */
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
