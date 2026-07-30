package ns.forge.tools;

// import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;

/* A tool that Claude can invoke
 */
public interface ForgeTool {
    /** Tool name as exposed to the model */
    String name();

    /**
     * The annotated POJO whose fields define the tool's JSON input schema. The SDK derives the
     * schema from Jackson annotations on this class.
     */
    Class<?> inputClass();

    /**
     * Parse the tool-use block's input and run the tool.
     *
     * @return a plain-text result to send back to the model
     * @throws Exception on any failure; the agent converts this into an error tool-result so the
     *     model can react to it
     */
    String execute(ToolUseBlock toolUse) throws Exception;

    // Tool spec(); // SDK tool definition
}
