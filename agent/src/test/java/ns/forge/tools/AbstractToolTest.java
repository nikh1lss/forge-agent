package ns.forge.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.models.messages.Tool;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Covers the JSON-to-POJO binding that every tool inherits. */
class AbstractToolTest {

    public static class Input {
        public String path;
        public int count;
        public boolean flag;
    }

    private static final class EchoTool extends AbstractTool<Input> {
        private Input received;

        EchoTool() {
            super(Input.class);
        }

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public Tool spec() {
            return Tool.builder().name("echo").build();
        }

        @Override
        protected String run(Input input) {
            received = input;
            return "ok";
        }
    }

    private final EchoTool tool = new EchoTool();

    private String execute(Map<String, Object> input) throws Exception {
        return tool.execute(ToolUses.of("echo", input));
    }

    /**
     * The input POJO carries no Jackson annotations, so this fails unless the binding mapper keeps
     * field auto-detection enabled — see the note on {@link AbstractTool}'s mapper.
     */
    @Test
    void bindsUnannotatedFieldsFromTheModelsJsonInput() throws Exception {
        assertEquals("ok", execute(Map.of("path", "src/Main.java", "count", 3, "flag", true)));

        assertEquals("src/Main.java", tool.received.path);
        assertEquals(3, tool.received.count);
        assertTrue(tool.received.flag);
    }

    @Test
    void leavesOmittedFieldsAtTheirDefaults() throws Exception {
        execute(Map.of("path", "only.txt"));

        assertEquals("only.txt", tool.received.path);
        assertEquals(0, tool.received.count);
    }

    @Test
    void toleratesAnEmptyInputObject() throws Exception {
        execute(Map.of());

        assertNull(tool.received.path);
    }

    /** A stray key from the model must not fail the whole call. */
    @Test
    void ignoresUnknownFields() throws Exception {
        execute(Map.of("path", "a.txt", "surprise", "unexpected"));

        assertEquals("a.txt", tool.received.path);
    }

    @Test
    void reportsTheToolNameWhenInputCannotBeBound() {
        Map<String, Object> input = new HashMap<>();
        input.put("path", List.of("not", "a", "string"));

        Exception e = assertThrows(IllegalArgumentException.class, () -> execute(input));

        assertTrue(e.getMessage().contains("invalid arguments for echo"), e.getMessage());
        assertNull(tool.received, "run() must not be reached with unbindable input");
    }
}
