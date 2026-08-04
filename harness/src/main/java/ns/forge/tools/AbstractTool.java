package ns.forge.tools;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Base class for tools with a typed input.
 *
 * <p>Handles the JSON-to-POJO parsing of the model's tool input so that concrete tools only
 * implement {@link #run(Object)} against their own input type. Generics never leak past this class.
 *
 * @param <T> the input POJO for this tool
 */
public abstract class AbstractTool<T> implements ForgeTool {

    /**
     * Mapper used to bind tool input onto the concrete input type.
     *
     * <p>We deliberately do not use {@code JsonValue.convert}: it runs on the SDK's internal
     * mapper, which disables field/getter/setter/creator auto-detection because every SDK model
     * annotates its properties explicitly. A plain input POJO has no annotations, so under that
     * mapper it exposes zero properties and every argument the model sends is rejected as an
     * unknown field. This mapper keeps auto-detection on, and ignores unknown fields so a stray key
     * from the model doesn't fail the whole call.
     */
    private static final ObjectMapper INPUT_MAPPER =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final Class<T> inputClass;

    protected AbstractTool(Class<T> inputClass) {
        this.inputClass = inputClass;
    }

    @Override
    public final String execute(ToolUseBlock toolUse) throws Exception {
        String json = ObjectMappers.jsonMapper().writeValueAsString(toolUse._input());

        T input;
        try {
            input = INPUT_MAPPER.readValue(json, inputClass);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid arguments for " + name() + ": " + json, e);
        }

        if (input == null) {
            throw new IllegalArgumentException("failed to parse tool input");
        }
        return run(input);
    }

    protected abstract String run(T input) throws Exception;
}
