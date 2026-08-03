# forge-agent

[![CI](https://github.com/nikh1lss/forge-agent/actions/workflows/ci.yml/badge.svg)](https://github.com/nikh1lss/forge-agent/actions/workflows/ci.yml)

A custom AI coding agent implemented in Java, built from scratch.

`forge` is a terminal chat loop implemented with the Anthropic Messages API. It gives Claude a
small set of file tools — read, list, edit — and lets it work directly in your current
directory: reading source, making targeted edits, and creating new files as it goes.

The whole thing is a few hundred lines of Java, built to show that a working code-editing
agent is mostly just a loop, a message history, and some tool definitions.

## Requirements

- JDK 24
- An Anthropic API key

## Setup

Put your key in a `.env` file at the repo root or set an environment variable:

```
ANTHROPIC_API_KEY=sk-ant-...
```

## Running

```bash
./gradlew run --console=plain
```

Other useful tasks:

```bash
./gradlew build            # compile + test
./gradlew installDist      # staged install under agent/build/install
```

## Tools

| Tool | Input | Behavior |
| --- | --- | --- |
| `read_file` | `path` | Returns the file's contents as text. |
| `list_files` | `path` (optional) | Walks up to 4 levels deep, returns a JSON array of entries. Skips `.git`, `.gradle`, `.idea`, `build`, `target`, `out`; truncates at 500 entries. |
| `edit_file` | `path`, `old_str`, `new_str` | Replaces `old_str` with `new_str`. `old_str` must match exactly once. If the file doesn't exist and `old_str` is empty, creates it (with parent directories) containing `new_str`. |

Tool failures aren't fatal: the exception message is returned to the model as an error
tool-result, so it can read what went wrong and try a different approach.

## Tests

```bash
./gradlew test
```

The suite drives each tool through `ForgeTool.execute(ToolUseBlock)` — the same entry point the
agent uses — rather than calling its internals, so the JSON-to-POJO binding is covered along with
the behavior. It pins the parts that are easy to get subtly wrong: `edit_file`'s uniqueness check
and non-overlapping match counting, `list_files`' depth limit, exclusion rules and truncation, and
`AbstractTool`'s tolerance of unknown fields from the model. CI runs it on every push and PR.

## How it works

```
Main ──► Agent ──► Anthropic Messages API
           │
           ├── Conversation    growing message history
           ├── AgentConfig     model, max tokens, system prompt, retry policy
           └── ToolRegistry    name → ForgeTool
```

`Agent.run()` is the loop:

1. Read a line from the user and append it to the `Conversation`.
2. Call the API with the full history, the system prompt, and every tool's spec.
3. Print any text blocks; execute any tool-use blocks.
4. If tools ran, append their results and go back to step 2 **without** prompting the
   user — that's what lets the model chain several tool calls into one turn. Otherwise,
   prompt again.

## Configuration

Defaults live in `AgentConfig.defaults()` — `claude-opus-5`, 4096 max tokens, 3 retries
with a 1s initial backoff, and the system prompt that tells forge to read before editing
and prefer small, targeted changes. `AgentConfig` is a record; construct one directly to
change any of it.

## TODO

A possible TUI, using some leaks as a reference..

## License

MIT
