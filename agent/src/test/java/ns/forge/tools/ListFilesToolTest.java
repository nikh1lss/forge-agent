package ns.forge.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ListFilesToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ListFilesTool tool = new ListFilesTool();

    @TempDir Path tmp;

    private List<String> list(Path dir) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("path", dir.toString());
        return MAPPER.readValue(
                tool.execute(ToolUses.of("list_files", input)), new TypeReference<>() {});
    }

    private void touch(String relative) throws IOException {
        Path file = tmp.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    @Test
    void listsFilesAndMarksDirectoriesWithATrailingSlash() throws Exception {
        touch("src/Main.java");
        Files.createDirectories(tmp.resolve("empty"));

        List<String> entries = list(tmp);

        assertTrue(entries.contains("src/"), entries.toString());
        assertTrue(entries.contains("src/Main.java"), entries.toString());
        assertTrue(entries.contains("empty/"), entries.toString());
    }

    @Test
    void doesNotIncludeTheRootDirectoryItself() throws Exception {
        touch("file.txt");

        assertEquals(List.of("file.txt"), list(tmp));
    }

    @Test
    void returnsAnEmptyArrayForAnEmptyDirectory() throws Exception {
        assertEquals(List.of(), list(tmp));
    }

    @Test
    void excludesBuildAndVcsDirectories() throws Exception {
        touch("keep.txt");
        touch(".git/config");
        touch(".gradle/cache.bin");
        touch(".idea/workspace.xml");
        touch("build/libs/app.jar");
        touch("target/classes/App.class");
        touch("out/production/App.class");

        List<String> entries = list(tmp);

        assertEquals(List.of("keep.txt"), entries);
    }

    @Test
    void excludesExcludedDirectoriesNestedBelowTheRoot() throws Exception {
        touch("module/src/App.java");
        touch("module/build/App.class");

        List<String> entries = list(tmp);

        assertTrue(entries.contains("module/src/App.java"), entries.toString());
        assertFalse(
                entries.stream().anyMatch(e -> e.contains("build")),
                "nested build/ must be excluded: " + entries);
    }

    @Test
    void walksAtMostFourLevelsDeep() throws Exception {
        touch("a/b/c/d/e/too-deep.txt");

        List<String> entries = list(tmp);

        assertTrue(entries.contains("a/b/c/d/"), entries.toString());
        assertFalse(entries.contains("a/b/c/d/e/"), entries.toString());
        assertFalse(entries.contains("a/b/c/d/e/too-deep.txt"), entries.toString());
    }

    @Test
    void truncatesAtFiveHundredEntries() throws Exception {
        for (int i = 0; i < 600; ++i) {
            touch("f" + i + ".txt");
        }

        List<String> entries = list(tmp);

        assertEquals(501, entries.size(), "500 entries plus the truncation marker");
        assertTrue(entries.get(500).contains("truncated at 500"), entries.get(500));
    }

    @Test
    void defaultsToTheCurrentDirectoryWhenPathIsBlank() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("path", "  ");

        List<String> entries =
                MAPPER.readValue(
                        tool.execute(ToolUses.of("list_files", input)), new TypeReference<>() {});

        assertFalse(entries.isEmpty(), "listing the working directory should find something");
    }

    @Test
    void defaultsToTheCurrentDirectoryWhenPathIsOmitted() throws Exception {
        List<String> entries =
                MAPPER.readValue(
                        tool.execute(ToolUses.of("list_files", Map.of())), new TypeReference<>() {});

        assertFalse(entries.isEmpty(), "listing the working directory should find something");
    }

    @Test
    void failsForADirectoryThatDoesNotExist() {
        assertThrows(IOException.class, () -> list(tmp.resolve("nope")));
    }
}
