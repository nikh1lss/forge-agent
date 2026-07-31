package ns.forge.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lists files and directories at a given path.
 *
 * <p>Walks at most {@link #MAX_DEPTH} levels down and skips directories like .git
 */
public final class ListFilesTool extends AbstractTool<ListFilesTool.Input> {
    private static final String NAME = "list_files";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DEPTH = 4;
    private static final int MAX_ENTRIES = 500;
    private static final Set<String> EXCLUDED_DIRS =
            Set.of(".git", ".gradle", ".idea", "build", "target", "out");

    public static class Input {
        public String path;
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
                        "List files and directories at a given relative path. "
                                + "Use this when you want to see what a directory contains. "
                                + "Common build and VCS directories (.git, build, target, ...) "
                                + "are excluded.")
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
                                                                        "Optional relative path of"
                                                                            + " a directory in the"
                                                                            + " working directory."
                                                                            + " Defaults to the"
                                                                            + " current"
                                                                            + " directory.")))
                                                .build())
                                .build())
                .build();
    }

    public ListFilesTool() {
        super(Input.class);
    }

    @Override
    protected String run(Input input) throws IOException {
        Path dir =
                (input.path == null || input.path.isBlank()) ? Path.of(".") : Path.of(input.path);

        List<String> entries = new ArrayList<>();
        boolean truncated = false;

        // No catch: the stream is closed by try-with-resources, and I/O failures
        // propagate to the caller, who owns the decision about a failed listing.
        try (Stream<Path> walk = Files.walk(dir, MAX_DEPTH)) {
            var iterator = walk.filter(p -> !isExcluded(dir, p)).iterator();

            while (iterator.hasNext()) {
                Path p = iterator.next();
                Path rel = dir.relativize(p);

                if (rel.toString().isEmpty()) {
                    continue;
                }

                if (entries.size() >= MAX_ENTRIES) {
                    truncated = true;
                    break;
                }
                entries.add(Files.isDirectory(p) ? rel + "/" : rel.toString());
            }
        }

        if (truncated) {
            entries.add("... (truncated at " + MAX_ENTRIES + " entries)");
        }

        return MAPPER.writeValueAsString(entries);
    }

    /** True if any path segment between root and p is an excluded directory. */
    private static boolean isExcluded(Path root, Path p) {
        Path rel = root.relativize(p);
        for (Path segment : rel) {
            if (EXCLUDED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
