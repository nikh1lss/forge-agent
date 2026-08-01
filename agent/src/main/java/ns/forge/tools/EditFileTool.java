package ns.forge.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Edits a text file by replacing one string with another, or creates a new file (with parent
 * directories) if it doesn't exist.
 */
public final class EditFileTool extends AbstractTool<EditFileTool.Input> {
    private static final String NAME = "edit_file";

    @Override
    public String name() {
        return NAME;
    }

    public static class Input {
        public String old_str;
        public String new_str;
        public String path;
    }

    @Override
    public Tool spec() {
        return Tool.builder()
                .name(NAME)
                .description(
                        "Make edits to a text file. Replaces 'old_str' with 'new_str' in the given"
                            + " file. 'old_str' must appear exactly once in the file and MUST be"
                            + " different from 'new_str'. If the file specified with path doesn't"
                            + " exist and 'old_str' is empty, the file will be created with"
                            + " 'new_str' as its content.")
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
                                                                        "The path to the file")))
                                                .putAdditionalProperty(
                                                        "old_str",
                                                        JsonValue.from(
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "Text to search for — must"
                                                                            + " match exactly and"
                                                                            + " must only have one"
                                                                            + " match exactly")))
                                                .putAdditionalProperty(
                                                        "new_str",
                                                        JsonValue.from(
                                                                Map.of(
                                                                        "type",
                                                                        "string",
                                                                        "description",
                                                                        "Text to replace old_str"
                                                                                + " with")))
                                                .build())
                                .build())
                .build();
    }

    public EditFileTool() {
        super(Input.class);
    }

    @Override
    protected String run(Input input) throws IOException {
        if (input.path == null || input.path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        String OldStr = input.old_str == null ? "" : input.old_str;
        String NewStr = input.new_str == null ? "" : input.new_str;
        if (OldStr.equals(NewStr)) {
            throw new IllegalArgumentException("old_str and new_str must be different");
        }

        Path filePath = Path.of(input.path);

        if (!Files.exists(filePath)) {
            if (!OldStr.isEmpty()) {
                throw new IllegalArgumentException("files does not exist at + " + input.path);
            }

            return createNewFile(filePath, NewStr);
        }

        String content = Files.readString(filePath);

        int occurrences = countOccurrences(content, OldStr);

        if (occurrences == 0) {
            throw new IllegalArgumentException("old_str not found in file");
        }

        if (occurrences > 1) {
            throw new IllegalArgumentException(
                    "old_str matches "
                            + occurrences
                            + " times; it must be unique. "
                            + "Include more surrounding context to disambiguate.");
        }

        Files.writeString(filePath, content.replace(OldStr, NewStr));

        return "OK";
    }

    private static String createNewFile(Path filepath, String newStr) {
        return null;
    }

    private static int countOccurrences(String content, String oldStr) {
        return 0;
    }
}
