package ns.forge.tools;

import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;

/* A tool that Claude can invoke
 */
public interface ForgeTool {
    /** Tool name as exposed to the model. Must match {@link #spec()}'s name. */
    String name();

    /**
     * The SDK tool definition sent to the model: name, description, and input schema. The agent
     * passes this to {@code MessageCreateParams.Builder.addTool}.
     */
    Tool spec();

    /**
     * Parse the tool-use block's input and run the tool.
     *
     * @return a plain-text result to send back to the model
     * @throws Exception on any failure; the agent converts this into an error tool-result so the
     *     model can react to it
     */
    String execute(ToolUseBlock toolUse) throws Exception;
}
