package ns.forge.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class EditFileToolTest {

    private final EditFileTool tool = new EditFileTool();

    @TempDir Path tmp;

    private String edit(Map<String, Object> input) throws Exception {
        return tool.execute(ToolUses.of("edit_file", input));
    }

    private String edit(Path path, String oldStr, String newStr) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("path", path.toString());
        input.put("old_str", oldStr);
        input.put("new_str", newStr);
        return edit(input);
    }

    private Path write(String name, String content) throws Exception {
        Path file = tmp.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void replacesUniqueOccurrence() throws Exception {
        Path file = write("hello.txt", "line one\nline two\nline three\n");

        assertEquals("OK", edit(file, "line two", "LINE TWO"));
        assertEquals("line one\nLINE TWO\nline three\n", Files.readString(file));
    }

    @Test
    void deletesTextWhenNewStrIsEmpty() throws Exception {
        Path file = write("hello.txt", "keep\ndrop\nkeep\n");

        assertEquals("OK", edit(file, "drop\n", ""));
        assertEquals("keep\nkeep\n", Files.readString(file));
    }

    @Test
    void matchesLiterallyRatherThanAsARegex() throws Exception {
        Path file = write("regex.txt", "a.c\nabc\n");

        // "a.c" as a regex would also match "abc"; String.replace is literal, so it must not.
        assertEquals("OK", edit(file, "a.c", "xyz"));
        assertEquals("xyz\nabc\n", Files.readString(file));
    }

    @Test
    void createsFileWhenMissingAndOldStrIsEmpty() throws Exception {
        Path file = tmp.resolve("new.txt");

        String result = edit(file, "", "fresh content");

        assertTrue(result.startsWith("Created file"), result);
        assertEquals("fresh content", Files.readString(file));
    }

    @Test
    void createsParentDirectoriesForNewFile() throws Exception {
        Path file = tmp.resolve("a/b/c/deep.txt");

        edit(file, "", "nested");

        assertEquals("nested", Files.readString(file));
    }

    @Test
    void rejectsMultipleOccurrences() throws Exception {
        Path file = write("dupes.txt", "todo\nsomething\ntodo\n");

        Exception e = assertThrows(IllegalArgumentException.class, () -> edit(file, "todo", "done"));

        assertTrue(e.getMessage().contains("2 times"), e.getMessage());
        assertEquals("todo\nsomething\ntodo\n", Files.readString(file), "file must be untouched");
    }

    @Test
    void countsOverlappingMatchesNonOverlapping() throws Exception {
        // "aa" occurs twice in "aaaa" when scanning past each match, not three times.
        Path file = write("overlap.txt", "aaaa");

        Exception e = assertThrows(IllegalArgumentException.class, () -> edit(file, "aa", "b"));

        assertTrue(e.getMessage().contains("2 times"), e.getMessage());
    }

    @Test
    void rejectsOldStrThatIsNotPresent() throws Exception {
        Path file = write("hello.txt", "some content\n");

        Exception e = assertThrows(
                IllegalArgumentException.class, () -> edit(file, "missing", "replacement"));

        assertTrue(e.getMessage().contains("not found"), e.getMessage());
        assertEquals("some content\n", Files.readString(file), "file must be untouched");
    }

    @Test
    void rejectsIdenticalOldAndNewStr() {
        Path file = tmp.resolve("hello.txt");

        assertThrows(IllegalArgumentException.class, () -> edit(file, "same", "same"));
    }

    @Test
    void rejectsEditToNonexistentFile() {
        Path file = tmp.resolve("nope.txt");

        Exception e = assertThrows(
                IllegalArgumentException.class, () -> edit(file, "old", "new"));

        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        assertFalse(Files.exists(file));
    }

    /**
     * Regression test: an empty old_str against an existing file used to reach countOccurrences
     * with a zero-length needle, where the scan index never advanced — hanging the agent forever.
     *
     * <p>SEPARATE_THREAD is required: the default same-thread timeout is only checked once the test
     * method returns, so it never fires on an infinite loop and would hang CI instead of failing.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsEmptyOldStrOnExistingFileWithoutHanging() throws Exception {
        Path file = write("exists.txt", "already here\n");

        Exception e = assertThrows(
                IllegalArgumentException.class, () -> edit(file, "", "overwrite"));

        assertTrue(e.getMessage().contains("already exists"), e.getMessage());
        assertEquals("already here\n", Files.readString(file), "file must be untouched");
    }

    @Test
    void rejectsBlankPath() {
        Map<String, Object> input = new HashMap<>();
        input.put("path", "   ");
        input.put("old_str", "a");
        input.put("new_str", "b");

        assertThrows(IllegalArgumentException.class, () -> edit(input));
    }

    @Test
    void rejectsMissingPath() {
        Map<String, Object> input = new HashMap<>();
        input.put("old_str", "a");
        input.put("new_str", "b");

        assertThrows(IllegalArgumentException.class, () -> edit(input));
    }

    @Test
    void treatsOmittedOldStrAsEmptyAndCreatesTheFile() throws Exception {
        Path file = tmp.resolve("implicit.txt");
        Map<String, Object> input = new HashMap<>();
        input.put("path", file.toString());
        input.put("new_str", "created without old_str");

        assertTrue(edit(input).startsWith("Created file"));
        assertEquals("created without old_str", Files.readString(file));
    }

    @Test
    void exposesRequiredFieldsInSpec() {
        var schema = tool.spec().inputSchema();

        assertEquals("edit_file", tool.spec().name());
        assertTrue(schema.required().orElseThrow().containsAll(
                java.util.List.of("path", "old_str", "new_str")));
    }
}
