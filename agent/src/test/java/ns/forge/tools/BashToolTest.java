package ns.forge.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class BashToolTest {

    @TempDir Path tmp;

    private String bash(String command) throws Exception {
        return new BashTool(tmp).execute(ToolUses.of("bash", Map.of("command", command)));
    }

    private String bash(String command, long timeoutMs) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("command", command);
        input.put("timeout_ms", timeoutMs);
        return new BashTool(tmp).execute(ToolUses.of("bash", input));
    }

    @Test
    void returnsStdoutAndAZeroExitCode() throws Exception {
        String result = bash("echo hello");

        assertTrue(result.contains("exit code: 0"), result);
        assertTrue(result.contains("hello"), result);
    }

    @Test
    void mergesStderrIntoTheOutput() throws Exception {
        String result = bash("echo to-stderr 1>&2");

        assertTrue(result.contains("to-stderr"), result);
    }

    @Test
    void reportsANonZeroExitCodeAsOutputRatherThanThrowing() throws Exception {
        String result = bash("echo failing; exit 3");

        assertTrue(result.contains("exit code: 3"), result);
        assertTrue(result.contains("failing"), result);
    }

    @Test
    void runsInTheConfiguredWorkingDirectory() throws Exception {
        Files.writeString(tmp.resolve("marker.txt"), "x");

        String result = bash("ls");

        assertTrue(result.contains("marker.txt"), result);
    }

    @Test
    void saysSoWhenACommandProducesNoOutput() throws Exception {
        String result = bash("true");

        assertTrue(result.contains("(no output)"), result);
    }

    @Test
    void killsACommandThatOutlivesItsTimeout() throws Exception {
        long start = System.nanoTime();
        String result = bash("sleep 30", 250);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(result.contains("timed out after 250ms"), result);
        assertTrue(elapsedMs < 10_000, "should not have waited for the full sleep: " + elapsedMs);
    }

    @Test
    void keepsOutputWrittenBeforeATimeout() throws Exception {
        String result = bash("echo early-output; sleep 30", 500);

        assertTrue(result.contains("early-output"), result);
    }

    @Test
    void capsAnOversizedTimeout() throws Exception {
        // The cap only matters for a command that would outlive it, so just check that an
        // absurd request still runs normally rather than overflowing or being rejected.
        String result = bash("echo ok", Long.MAX_VALUE);

        assertTrue(result.contains("exit code: 0"), result);
        assertTrue(result.contains("ok"), result);
    }

    @Test
    void fallsBackToTheDefaultTimeoutForANonPositiveValue() throws Exception {
        String result = bash("echo ok", -1);

        assertTrue(result.contains("exit code: 0"), result);
        assertFalse(result.contains("timed out"), result);
    }

    @Test
    void doesNotLeaveALargeCommandBlockedOnAFullPipe() throws Exception {
        // Far more than a pipe buffer holds: if nothing drained it concurrently the command
        // would block forever and this test would hang rather than fail.
        String result = bash("head -c 400000 /dev/zero | tr '\\0' 'a'");

        assertTrue(result.contains("exit code: 0"), result);
        assertTrue(result.contains("truncated"), result);
    }

    @Test
    void truncatesTheMiddleAndKeepsBothEndsOfLongOutput() throws Exception {
        String result = bash("echo FIRST-MARKER; head -c 100000 /dev/zero | tr '\\0' 'b'; echo;"
                + " echo LAST-MARKER");

        assertTrue(result.contains("FIRST-MARKER"), "head of the output must survive");
        assertTrue(result.contains("LAST-MARKER"), "tail of the output must survive");
        assertTrue(result.contains("characters truncated"), result);
        assertTrue(
                result.length() < BashTool.MAX_OUTPUT_CHARS + 200,
                "output should be capped, was " + result.length());
    }

    @Test
    void rejectsAMissingCommand() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BashTool(tmp).execute(ToolUses.of("bash", Map.of())));
    }

    @Test
    void rejectsABlankCommand() {
        assertThrows(IllegalArgumentException.class, () -> bash("   "));
    }

    @Test
    void exposesItsNameAndSchema() {
        BashTool tool = new BashTool(tmp);

        assertEquals("bash", tool.name());
        assertEquals("bash", tool.spec().name());
        assertTrue(
                tool.spec().inputSchema().required().orElseThrow().contains("command"),
                "command must be declared required");
    }
}
