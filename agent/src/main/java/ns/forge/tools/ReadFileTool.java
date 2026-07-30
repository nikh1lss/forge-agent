package ns.forge.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/*
 * Reads the contents of a file at a given relative path
 */
public final class ReadFileTool extends AbstractTool<ReadFileTool.Input> {
    private static final String NAME = "read_file";

    public static class Input {
        public String path;
    }

    public ReadFileTool() {
        super(Input.class);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Tool spec() {
        return Tool.builder()
                .name(NAME)
                .description(
                        "Read the contents of a given relative file path. "
                                + "Use this when you want to see what's inside a file. "
                                + "Do not use this with directory names.")
                .inputSchema(
                        Tool.InputSchema.builder()
                                .properties(
                                        Tool.InputSchema.Properties.builder()
                                                .putAdditionalProperty(
                                                        "path",
                                                        JsonValue.from(
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "The relative path of a"
                                                                            + " file in the working"
                                                                            + " directory.")))
                                                .build())
                                .required(List.of("path"))
                                .build())
                .build();
    }

    @Override
    protected String run(Input input) throws IOException {
        if (input.path == null || input.path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }

        return Files.readString(Path.of(input.path));
    }
}
