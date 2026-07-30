package ns.forge.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * An immutable name-indexed collection of tools
 */
public final class ToolRegistry {
    private final Map<String, ForgeTool> toolsByName;

    public ToolRegistry(List<ForgeTool> tools) {
        Map<String, ForgeTool> byName = new LinkedHashMap<>();
        for (ForgeTool tool : tools) {
            if (byName.put(tool.name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate tool name: " + tool.name());
            }
        }
        this.toolsByName = byName;
    }

    public Optional<ForgeTool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public Iterable<ForgeTool> all() {
        return toolsByName.values();
    }
}
