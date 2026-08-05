import { Box, Text, useInput } from 'ink';
import { useEffect, useReducer, useRef, useState } from 'react';

import type { Item, ToolCall } from './state.js';

const LOGO = [
    "\n" +
    "███████████    ███████    ███████████     █████████  ██████████" + "\n" +
    "▒▒███▒▒▒▒▒▒█  ███▒▒▒▒▒███ ▒▒███▒▒▒▒▒███   ███▒▒▒▒▒███▒▒███▒▒▒▒▒█" + "\n" +
    " ▒███   █ ▒  ███     ▒▒███ ▒███    ▒███  ███     ▒▒▒  ▒███  █ ▒ " + "\n" +
    " ▒███████   ▒███      ▒███ ▒██████████  ▒███          ▒██████   " + "\n" +
    " ▒███▒▒▒█   ▒███      ▒███ ▒███▒▒▒▒▒███ ▒███    █████ ▒███▒▒█   " + "\n" +
    " ▒███  ▒    ▒▒███     ███  ▒███    ▒███ ▒▒███  ▒▒███  ▒███ ▒   █" + "\n" +
    " █████       ▒▒▒███████▒   █████   █████ ▒▒█████████  ██████████" + "\n" +
    "▒▒▒▒▒          ▒▒▒▒▒▒▒    ▒▒▒▒▒   ▒▒▒▒▒   ▒▒▒▒▒▒▒▒▒  ▒▒▒▒▒▒▒▒▒▒ " + "\n\n"];


const SPINNER_FRAMES = [
    "▰▱▱▱▱▱▱ ",
    "▰▰▱▱▱▱▱ ",
    "▰▰▰▱▱▱▱ ",
    "▰▰▰▰▱▱▱ ",
    "▰▰▰▰▰▱▱ ",
    "▰▰▰▰▰▰▱ ",
    "▰▰▰▰▰▰▰ ",
    "▰▱▱▱▱▱▱ "
];

export function Spinner() {
    const [frame, setFrame] = useState(0);

    useEffect(() => {
        const timer = setInterval(() => setFrame((n) => (n + 1) % SPINNER_FRAMES.length), 80);
        return () => clearInterval(timer);
    }, []);

    return <Text color="yellow">{SPINNER_FRAMES[frame]}</Text>;
}

function truncate(text: string, max: number): string {
    return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

/**
 * Renders a tool's arguments the way a caller would write them, not as JSON.
 *
 * The values carry the meaning — `bash({"command":"ls"})` says nothing `bash(ls)` doesn't — and the
 * input arrives as a fragment while it's still streaming, so anything unparseable is shown raw
 * rather than dropped.
 */
function formatArgs(input: string): string {
    if (!input.trim()) return '';

    try {
        const parsed: unknown = JSON.parse(input);
        if (typeof parsed !== 'object' || parsed === null) return truncate(String(parsed), 60);

        const values = Object.values(parsed as Record<string, unknown>).map((value) =>
            typeof value === 'string' ? value : JSON.stringify(value),
        );
        return truncate(values.join(', ').replace(/\s+/g, ' '), 60);
    } catch {
        return truncate(input.replace(/\s+/g, ' '), 60);
    }
}

/**
 * One line standing in for output that can run to thousands.
 *
 * `bash` prefixes its result with an exit code. A successful one says nothing the green marker
 * doesn't, and leaving it in would make every shell command summarize as "exit code: 0" instead of
 * what it actually printed — so it is dropped, while a failing one is kept and leads.
 */
function summarize(content: string): string {
    let lines = content.split('\n').filter((line) => line.trim());

    if (lines[0] === 'exit code: 0' && lines.length > 1) lines = lines.slice(1);

    const [first] = lines;
    if (first === undefined) return '(no output)';

    return lines.length > 1
        ? `${truncate(first, 60)}  (+${lines.length - 1} more lines)`
        : truncate(first, 76);
}

export function ToolView({ tool }: { tool: ToolCall }) {
    const args = formatArgs(tool.input);
    const running = tool.result === undefined;

    return (
        <Box flexDirection="column">
            <Text>
                <Text color={tool.isError ? 'red' : 'green'}>⏺ </Text>
                <Text bold>{tool.name}</Text>
                <Text dimColor>({args})</Text>
            </Text>
            <Text dimColor>
                {'  ↳ '}
                {running ? <Spinner /> : null}
                {running ? ' running…' : summarize(tool.result ?? '')}
            </Text>
        </Box>
    );
}

export function ItemView({ item }: { item: Item }) {
    switch (item.kind) {
        case 'banner':
            return (
                <Box flexDirection="column" marginBottom={1}>
                    <Text>
                        <Text color="red" bold>
                            {LOGO}
                        </Text>
                        <Text dimColor> · {item.model}</Text>
                    </Text>
                    <Text dimColor>{item.cwd}</Text>
                </Box>
            );

        case 'user':
            return (
                <Box marginBottom={1}>
                    <Text color="cyan">{'λ '}</Text>
                    <Text>{item.text}</Text>
                </Box>
            );

        case 'assistant':
            return (
                <Box marginBottom={1}>
                    <Text>{item.text}</Text>
                </Box>
            );

        case 'thinking':
            return (
                <Box marginBottom={1}>
                    <Text dimColor italic>
                        {item.text}
                    </Text>
                </Box>
            );

        case 'tool':
            return (
                <Box marginBottom={1}>
                    <ToolView tool={item.tool} />
                </Box>
            );

        case 'notice':
            return (
                <Box marginBottom={1}>
                    <Text dimColor>{item.text}</Text>
                </Box>
            );

        case 'error':
            return (
                <Box marginBottom={1}>
                    <Text color="red">error: {item.text}</Text>
                </Box>
            );
    }
}

/**
 * The input line, pinned below the transcript.
 *
 * Editing is deliberately minimal — insert, backspace, and move along the line. Ink redraws the
 * whole component on every keystroke, so there is no cursor to move in the terminal itself; the
 * position is state, and the character under it is drawn inverted.
 */
export function Prompt({ onSubmit }: { onSubmit: (text: string) => void }) {
    // The buffer lives in a ref, not state. A paste — or just fast typing — delivers several chunks
    // before React re-renders, and a handler closed over state would read a stale value for every
    // chunk after the first, silently dropping characters.
    const buffer = useRef({ value: '', cursor: 0 });
    const [, redraw] = useReducer((n: number) => n + 1, 0);

    useInput((input, key) => {
        const state = buffer.current;

        if (key.leftArrow) {
            state.cursor = Math.max(0, state.cursor - 1);
            redraw();
            return;
        }

        if (key.rightArrow) {
            state.cursor = Math.min(state.value.length, state.cursor + 1);
            redraw();
            return;
        }

        // Terminals disagree about which of these the backspace key sends.
        if (key.backspace || key.delete) {
            if (state.cursor > 0) {
                state.value = state.value.slice(0, state.cursor - 1) + state.value.slice(state.cursor);
                state.cursor -= 1;
            }
            redraw();
            return;
        }

        // Ink reports chords with the character still in `input`, so these have to be dropped
        // explicitly or ctrl-a would type an "a". Ctrl-C is Ink's own and never arrives here.
        if (key.ctrl || key.meta || key.escape || key.tab || key.upArrow || key.downArrow) return;

        // One chunk can carry text and a newline together: a paste, or a terminal sending a whole line
        // at once. Insert the text before acting on the newline, or the submit takes an empty buffer
        // and the text is lost.
        const typed = input.replace(/[\r\n]/g, '');
        const submitting = key.return || /[\r\n]/.test(input);

        if (typed) {
            state.value = state.value.slice(0, state.cursor) + typed + state.value.slice(state.cursor);
            state.cursor += typed.length;
        }

        if (submitting) {
            const text = state.value.trim();
            if (text) {
                state.value = '';
                state.cursor = 0;
                onSubmit(text);
            }
        }

        redraw();
    });

    const { value, cursor } = buffer.current;
    const under = value[cursor] ?? ' ';

    return (
        <Box borderStyle="round" borderColor="gray" paddingX={1}>
            <Text color="cyan">{'λ '}</Text>
            <Text>
                {value.slice(0, cursor)}
                <Text inverse>{under}</Text>
                {value.slice(cursor + 1)}
            </Text>
            {value === '' && <Text dimColor>Ask forge to change something…</Text>}
        </Box>
    );
}
