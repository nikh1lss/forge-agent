/**
 * The harness's side of the wire, mirrored from `ns.forge.ui.ForgeEvent`.
 *
 * Field names are the Anthropic API's snake_case, which is why they are spelled that way here and
 * camelCase everywhere else. Change this file and ForgeEvent.java together.
 */
export type ForgeEvent =
  | { type: "ready"; cwd: string; model: string }
  | { type: "awaiting_input" }
  | { type: "turn_start" }
  | { type: "text_delta"; text: string }
  | { type: "text_end" }
  | { type: "thinking_delta"; text: string }
  | { type: "thinking_end" }
  | { type: "tool_start"; id: string; name: string }
  | { type: "tool_input_delta"; id: string; partial_json: string }
  | { type: "tool_result"; id: string; content: string; is_error: boolean }
  | {
      type: "turn_end";
      stop_reason: string;
      input_tokens: number;
      output_tokens: number;
    }
  | { type: "notice"; message: string }
  | { type: "error"; message: string };

/** A user turn, the only thing sent back the other way. */
export interface UserMessage {
  type: "user";
  text: string;
}

/**
 * Parses one line from the harness, returning null for anything unrecognized.
 *
 * A newer harness may emit event types this build has never heard of. Dropping them keeps the TUI
 * running rather than crashing on a field it doesn't know about.
 */
export function parseEvent(line: string): ForgeEvent | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(line);
  } catch {
    return null;
  }

  if (typeof parsed !== "object" || parsed === null) return null;
  if (typeof (parsed as { type?: unknown }).type !== "string") return null;

  return parsed as ForgeEvent;
}
