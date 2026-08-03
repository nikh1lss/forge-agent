package ns.forge.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUseBlock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class ToolRegistryTest {

    /** Minimal tool: the registry only ever looks at {@link ForgeTool#name()}. */
    private record NamedTool(String name) implements ForgeTool {
        @Override
        public Tool spec() {
            return Tool.builder().name(name).build();
        }

        @Override
        public String execute(ToolUseBlock toolUse) {
            return name;
        }
    }

    @Test
    void findsARegisteredToolByName() {
        ForgeTool read = new NamedTool("read_file");
        ToolRegistry registry = new ToolRegistry(List.of(read, new NamedTool("edit_file")));

        assertSame(read, registry.find("read_file").orElseThrow());
    }

    @Test
    void returnsEmptyForAnUnknownName() {
        ToolRegistry registry = new ToolRegistry(List.of(new NamedTool("read_file")));

        assertTrue(registry.find("write_file").isEmpty());
        assertTrue(registry.find("").isEmpty());
    }

    @Test
    void rejectsDuplicateToolNames() {
        List<ForgeTool> tools = List.of(new NamedTool("read_file"), new NamedTool("read_file"));

        Exception e = assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(tools));

        assertTrue(e.getMessage().contains("read_file"), e.getMessage());
    }

    @Test
    void preservesRegistrationOrder() {
        ToolRegistry registry =
                new ToolRegistry(
                        List.of(
                                new NamedTool("read_file"),
                                new NamedTool("list_files"),
                                new NamedTool("edit_file")));

        List<String> names = new ArrayList<>();
        registry.all().forEach(tool -> names.add(tool.name()));

        assertEquals(List.of("read_file", "list_files", "edit_file"), names);
    }

    @Test
    void supportsAnEmptyRegistry() {
        ToolRegistry registry = new ToolRegistry(List.of());

        assertTrue(registry.find("anything").isEmpty());
        assertTrue(registry.all().iterator().hasNext() == false);
    }
}
