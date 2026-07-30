package ns.forge.tools;

import com.anthropic.models.messages.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * An immutable name-indexed collection of tools
 */
public final class ToolRegistry {
    private final Map<String, Tool> toolsByName;

    public ToolRegistry(List<Tool> tools) {
        Map<String, Tool> byName = new LinkedHashMap<>();
        for (Tool tool : tools) {
            if (byName.put(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate tool name: " + tool.name());
            }
        }
        this.toolsByName = byName;
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public Iterable<Tool> all() {
        return toolsByName.values();
    }
}
