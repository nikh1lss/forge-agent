import { Box, Static, Text, useApp } from 'ink';
import { useCallback, useEffect, useReducer, useRef, useState } from 'react';

import { Engine } from './engine.js';
import type { ForgeEvent } from './protocol.js';
import { initialState, reduce, submitted, type State } from './state.js';
import { ItemView, Prompt, Spinner, ToolView } from './ui.js';

type Action = { kind: 'event'; event: ForgeEvent } | { kind: 'submit'; text: string };

function apply(state: State, action: Action): State {
    return action.kind === 'event' ? reduce(state, action.event) : submitted(state, action.text);
}

/**
 * The whole TUI: finished transcript above, live turn and input below.
 *
 * The split is what `<Static>` needs. Ink writes static items to the terminal once and then only
 * redraws what is under them, so a session that has scrolled for an hour still costs one line of
 * redraw per keystroke. Anything still changing — streaming text, a tool that hasn't returned —
 * has to stay out of it until it settles.
 */
export function App({ onFatal }: { onFatal: (message: string) => void }) {
    const [state, dispatch] = useReducer(apply, initialState);
    const { exit } = useApp();
    const engine = useRef<Engine | null>(null);
    const [exited, setExited] = useState(false);

    useEffect(() => {
        let started: Engine;

        try {
            started = new Engine({
                onEvent: (event) => dispatch({ kind: 'event', event }),
                onExit: (code, stderr) => {
                    if (code !== 0) {
                        onFatal(
                            `forge exited with code ${code ?? 'null'}` + (stderr ? `\n\n${stderr.trim()}` : ''),
                        );
                    }
                    setExited(true);
                    exit();
                },
            });
        } catch (error) {
            onFatal(error instanceof Error ? error.message : String(error));
            exit();
            return;
        }

        engine.current = started;
        return () => started.kill();
    }, [exit, onFatal]);

    const submit = useCallback((text: string) => {
        dispatch({ kind: 'submit', text });
        engine.current?.send(text);
    }, []);

    return (
        <>
            <Static items={state.history}>{(item) => <ItemView key={item.key} item={item} />}</Static>

            <Box flexDirection="column">
                {state.thinking !== '' && (
                    <Box marginBottom={1}>
                        <Text dimColor italic>
                            {state.thinking}
                        </Text>
                    </Box>
                )}

                {state.text !== '' && (
                    <Box marginBottom={1}>
                        <Text>{state.text}</Text>
                    </Box>
                )}

                {state.tools.map((tool) => (
                    <Box key={tool.id} marginBottom={1}>
                        <ToolView tool={tool} />
                    </Box>
                ))}

                {state.status !== 'idle' && !exited && (
                    <Box marginBottom={1}>
                        <Spinner />
                        <Text dimColor>{state.status === 'starting' ? '  starting forge…' : ' working…'}</Text>
                    </Box>
                )}

                {!exited && <Prompt onSubmit={submit} />}
            </Box>
        </>
    );
}
