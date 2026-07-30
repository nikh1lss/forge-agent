package ns.forge.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReadFileTool extends AbstractTool<ReadFileTool.Input> {
    @JsonTypeName("read_file")
    @JsonClassDescription(
            "Read the contents of a given relative file path. "
                    + "Use this when you want to see what's inside a file. "
                    + "Do not use this with directory names.")
    public static class Input {
        @JsonPropertyDescription("The relative path of a file in the working directory.")
        public String path;
    }

    public ReadFileTool() {
        super(Input.class);
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    protected String run(Input input) throws IOException {
        if (input.path == null || input.path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }

        return Files.readString(Path.of(input.path));
    }
}
