package ns.forge;

// import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;

public interface ForgeTool {
    String name();

    Class<?> inputClass();

    String execute(ToolUseBlock toolUse) throws Exception;

    // Tool spec(); // SDK tool definition
}
