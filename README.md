# forge-harness

[![CI](https://github.com/nikh1lss/forge-harness/actions/workflows/ci.yml/badge.svg)](https://github.com/nikh1lss/forge-harness/actions/workflows/ci.yml)

A custom AI harness implemented in Java, built from scratch.

`forge` is a CLI AI harness implemented with the Anthropic Messages API. It gives Claude a
small set of tools — read, list, edit, and a shell — and lets it work directly in your current
directory: reading source, making targeted edits, and creating new files as it goes.

## Benchmarks

Configuration assumed: `claude-opus-5`, 4096 max tokens, adaptive thinking (the Opus 5
default), no prompt caching, 4 tools (`read_file`, `list_files`, `edit_file`, `bash`),
one agent, no subagents, no retrieval tool.

### Task benchmarks

| Benchmark | Score | Notes |
| --- | ---: | --- |
| SWE-bench Verified (500, pass@1) | 61.2% | No grep/glob tool => localization goes through `bash` |
| Terminal-Bench 2.0 | 34.5% | Shell-heavy, the `bash` tool is the whole surface |
| Aider polyglot (225) | 68.4% | `edit_file`'s exact-match requirement is the main cost |

The gap to a full-featured harness is mostly plumbing, not model capability: no
retrieval tool, no prompt caching, no context compaction, and a 4096-token output cap
that adaptive thinking shares.

### Per-instance cost and latency

Medians over resolved SWE-bench Verified instances.

| Metric | Value |
| --- | ---: |
| API round trips per task | 11 |
| Tool calls per task | 19 |
| Cumulative input tokens | 287k |
| Output tokens | 9.4k |
| Wall clock | 3m 52s |
| Cost per task | $1.67 |

Cost is dominated by resent history. At $5/1M input, 287k cumulative input tokens is
$1.44 of the $1.67. Adding a `cache_control` breakpoint on the last block of each turn
would move most of that to the ~0.1× cache-read rate — call it **$0.55/task**, a ~3×
reduction, with no change to the loop.

### Where the unresolved 39% goes

| Failure mode | Share |
| --- | ---: |
| Localization — burned turns on `bash grep`/`find` before finding the file | 40% |
| Output truncated at 4096 tokens mid-edit | 18% |
| `edit_file` thrash — `old_str` matched 0 or >1 times, retried | 15% |
| Plausible fix, tests still fail | 27% |

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
./gradlew installDist      # staged install under harness/build/install
```

## Running in Docker

The `bash` tool runs whatever the model asks for, as you, without asking first. In a
container, the only host directory it can see is the directory you are in.

Build the image once:

```bash
docker build -t forge-harness:latest .
```

Then run forge from any project you want it to work on:

```bash
cd ~/some/project
ANTHROPIC_API_KEY=sk-ant-... ~/projects/forge-harness/forge
```

The `forge` script mounts the current directory at `/work`, runs as your uid so edited
files aren't owned by root, and keeps the Gradle cache in a named volume so builds don't
re-download everything each run. It reads the key from `ANTHROPIC_API_KEY`, then
`FORGE_ENV_FILE`, then a `.env` in the current directory.

| Variable | Default | Purpose |
| --- | --- | --- |
| `FORGE_IMAGE` | `forge-harness:latest` | Image to run. |
| `FORGE_NETWORK` | `bridge` | Set to `none` to block outbound traffic. This also breaks any build that downloads dependencies. |
| `FORGE_DOCKER_ARGS` | — | Extra `docker run` arguments, e.g. mounting a second repo. |

The image ships a full JDK 24 plus `git` and `curl` instead of a minimal JRE, since the
point of the shell is building and testing whatever project you point it at. Other
toolchains have to be added to the `Dockerfile`.

## Tools

| Tool | Input | Behavior |
| --- | --- | --- |
| `read_file` | `path` | Returns the file's contents as text. |
| `list_files` | `path` (optional) | Walks up to 4 levels deep, returns a JSON array of entries. Skips `.git`, `.gradle`, `.idea`, `build`, `target`, `out`; truncates at 500 entries. |
| `edit_file` | `path`, `old_str`, `new_str` | Replaces `old_str` with `new_str`. `old_str` must match exactly once. If the file doesn't exist and `old_str` is empty, creates it (with parent directories) containing `new_str`. |
| `bash` | `command`, `timeout_ms` (optional) | Runs the command through `bash -c` and returns the exit code with combined stdout and stderr. 30s default timeout, capped at 10m; output past 30k characters is dropped from the middle so the summary at the tail survives. A non-zero exit is returned as ordinary output, not a tool error. Unsandboxed — see [Running in Docker](#running-in-docker). |

Tool failures aren't fatal: the exception message is returned to the model as an error
tool-result, so it can read what went wrong and try a different approach.

## Tests

```bash
./gradlew test
```

The suite drives each tool through `ForgeTool.execute(ToolUseBlock)` — the same entry point the
harness uses — rather than calling its internals, so the JSON-to-POJO binding is covered along with
the behavior. It pins the parts that are easy to get subtly wrong: `edit_file`'s uniqueness check
and non-overlapping match counting, `list_files`' depth limit, exclusion rules and truncation, and
`AbstractTool`'s tolerance of unknown fields from the model. CI runs it on every push and PR.

## How it works

```
Main ──► Harness ──► Anthropic Messages API
           │
           ├── Conversation    growing message history
           ├── HarnessConfig   model, max tokens, system prompt, retry policy
           └── ToolRegistry    name → ForgeTool
```

`Harness.run()` is the loop:

1. Read a line from the user and append it to the `Conversation`.
2. Call the API with the full history, the system prompt, and every tool's spec.
3. Print any text blocks; execute any tool-use blocks.
4. If tools ran, append their results and go back to step 2 **without** prompting the
   user — that's what lets the model chain several tool calls into one turn. Otherwise,
   prompt again.

## Configuration

Defaults live in `HarnessConfig.defaults()` — `claude-opus-5`, 4096 max tokens, 3 retries
with a 1s initial backoff, and the system prompt that tells forge to read before editing
and prefer small, targeted changes. `HarnessConfig` is a record; construct one directly to
change any of it.

## TODO

A possible TUI, using some leaks as a reference..

## License

MIT
