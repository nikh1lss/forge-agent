#!/usr/bin/env node
import { render } from 'ink';

import { App } from './app.js';

let fatal: string | null = null;

const app = render(<App onFatal={(message) => (fatal = message)} />);

await app.waitUntilExit();

// Printed after unmount so it isn't overwritten by Ink's final redraw.
if (fatal !== null) {
  console.error(fatal);
  process.exitCode = 1;
}
