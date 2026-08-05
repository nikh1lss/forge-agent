import { spawn, type ChildProcess } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { parseEvent, type ForgeEvent } from './protocol.js';

/** Repo root, from either `tui/src` under tsx or `tui/dist` after a build. */
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

const localEngine = resolve(repoRoot, 'harness/build/install/harness/bin/harness');
const dockerEngine = resolve(repoRoot, 'forge');

/**
 * Variables from the repo root's `.env`, for anything not already in the environment.
 *
 * The harness resolves its key relative to its own working directory, and that directory is the
 * project being worked on rather than this repo — running the TUI from `tui/`, which is what the
 * README's `cd tui && npm start` does, otherwise leaves the key at the repo root unreachable. The
 * docker path benefits too: the `forge` script prefers `ANTHROPIC_API_KEY` over its own `.env`
 * lookup, so exporting it here is enough for both.
 *
 * Deliberately not a full dotenv implementation — no interpolation, no multi-line values. This
 * reads the one file the harness would have read itself, and a real environment variable still
 * wins, which is the ordering both the harness and the `forge` script already use.
 */
function repoEnv(): NodeJS.ProcessEnv {
  const file = resolve(repoRoot, '.env');
  if (!existsSync(file)) return {};

  const parsed: NodeJS.ProcessEnv = {};

  let contents: string;
  try {
    contents = readFileSync(file, 'utf8');
  } catch {
    // An unreadable .env is not worth failing the launch over: the harness reports a missing key
    // clearly enough on its own, and the variable may well be set some other way.
    return {};
  }

  for (const line of contents.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;

    const key = trimmed.slice(0, eq).trim().replace(/^export\s+/, '');
    const value = trimmed.slice(eq + 1).trim();

    if (key in process.env) continue;

    // Strip one layer of matching quotes, the only quoting `.env` files conventionally use.
    parsed[key] = /^(".*"|'.*')$/s.test(value) ? value.slice(1, -1) : value;
  }

  return parsed;
}

export interface EngineHandlers {
  onEvent: (event: ForgeEvent) => void;
  /** Called once when the harness exits, with whatever it left on stderr. */
  onExit: (code: number | null, stderr: string) => void;
}

/**
 * The harness as a child process speaking JSON lines.
 *
 * `FORGE_DOCKER=1` runs it through the repo's `forge` script instead of directly, which reuses that
 * script's mounts, uid mapping and key resolution rather than duplicating them here. The script only
 * asks docker for a TTY when it has one, and stdio is piped here, so it does the right thing
 * unmodified.
 */
export class Engine {
  private readonly child: ChildProcess;

  /** Bytes after the last newline — a JSON object can be split across two chunks. */
  private buffer = '';

  /** Kept whole so a startup failure (bad key, missing image) can be shown verbatim. */
  private stderr = '';

  constructor(handlers: EngineHandlers) {
    const docker = process.env['FORGE_DOCKER'] === '1';
    const command = docker ? dockerEngine : localEngine;

    if (!existsSync(command)) {
      throw new Error(
        docker
          ? `no forge script at ${command}`
          : `no engine at ${command} — run \`npm run build:engine\``,
      );
    }

    this.child = spawn(command, ['--jsonl'], {
      // The directory the TUI was launched from is the one forge should work on. Its key, though,
      // belongs to this repo rather than to that directory — hence the separate lookup.
      cwd: process.cwd(),
      env: { ...process.env, ...repoEnv() },
      stdio: ['pipe', 'pipe', 'pipe'],
    });

    this.child.stdout?.setEncoding('utf8');
    this.child.stdout?.on('data', (chunk: string) => {
      this.buffer += chunk;

      const lines = this.buffer.split('\n');
      // A trailing fragment stays buffered; a trailing newline leaves '' here, which is a no-op.
      this.buffer = lines.pop() ?? '';

      for (const line of lines) {
        if (!line.trim()) continue;
        const event = parseEvent(line);
        if (event) handlers.onEvent(event);
      }
    });

    this.child.stderr?.setEncoding('utf8');
    this.child.stderr?.on('data', (chunk: string) => {
      this.stderr += chunk;
    });

    this.child.on('error', (error) => {
      this.stderr += `${error.message}\n`;
      handlers.onExit(null, this.stderr);
    });

    this.child.on('close', (code) => handlers.onExit(code, this.stderr));
  }

  /**
   * Queues a user turn.
   *
   * There is no need to check whether the harness is ready for one: it reads stdin only when its
   * loop comes back around, so the pipe holds anything sent early.
   */
  send(text: string): void {
    this.child.stdin?.write(`${JSON.stringify({ type: 'user', text })}\n`);
  }

  /** Closes stdin, which the harness treats as end of session. */
  stop(): void {
    this.child.stdin?.end();
  }

  kill(): void {
    this.child.kill();
  }
}
