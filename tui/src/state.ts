import type { ForgeEvent } from "./protocol.js";

export interface ToolCall {
  id: string;
  name: string;
  /** Raw JSON, assembled from `tool_input_delta` fragments. Empty for a no-argument call. */
  input: string;
  result?: string;
  isError?: boolean;
}

/**
 * Items are only ever appended, never edited — Ink's `<Static>` renders each one once and then
 * leaves it alone in the terminal's scrollback, which is what lets a long session scroll normally
 * instead of being re-rendered on every keystroke. Anything still changing lives in the live fields
 * below and moves here once it stops.
 */
export type Item =
  | { kind: "banner"; key: number; cwd: string; model: string }
  | { kind: "user"; key: number; text: string }
  | { kind: "assistant"; key: number; text: string }
  | { kind: "thinking"; key: number; text: string }
  | { kind: "tool"; key: number; tool: ToolCall }
  | { kind: "notice"; key: number; text: string }
  | { kind: "error"; key: number; text: string };

export type Status = "starting" | "idle" | "working";

export interface State {
  history: Item[];
  /** Assistant text streaming right now, not yet committed to history. */
  text: string;
  thinking: string;
  /** Calls the model has started but that haven't returned; rendered under the live text. */
  tools: ToolCall[];
  status: Status;
  cwd: string;
  model: string;
  nextKey: number;
}

export const initialState: State = {
  history: [],
  text: "",
  thinking: "",
  tools: [],
  status: "starting",
  cwd: process.cwd(),
  model: "",
  nextKey: 0,
};

/**
 * An item before it has a key.
 *
 * Written as a conditional type so it distributes over the union — a bare `Omit<Item, 'key'>`
 * collapses to the properties every variant shares, which is only `kind`.
 */
type NewItem<T = Item> = T extends Item ? Omit<T, "key"> : never;

function append(state: State, item: NewItem): State {
  return {
    ...state,
    history: [...state.history, { ...item, key: state.nextKey } as Item],
    nextKey: state.nextKey + 1,
  };
}

/** Records a user turn the moment it's submitted, before the harness has read it. */
export function submitted(state: State, text: string): State {
  return { ...append(state, { kind: "user", text }), status: "working" };
}

export function reduce(state: State, event: ForgeEvent): State {
  switch (event.type) {
    // The banner goes into history rather than the live region: it is written once, and everything
    // outside <Static> renders below the transcript, which is the wrong end for a header.
    case "ready":
      return {
        ...append(state, {
          kind: "banner",
          cwd: event.cwd,
          model: event.model,
        }),
        cwd: event.cwd,
        model: event.model,
      };

    case "awaiting_input":
      return { ...state, status: "idle" };

    // Emitted once per attempt, so a retried request lands here again. Dropping the live buffers is
    // the point: a stream that died mid-sentence would otherwise leave half a word on screen and
    // the retry would append to it.
    case "turn_start":
      return { ...state, status: "working", text: "", thinking: "", tools: [] };

    case "text_delta":
      return { ...state, text: state.text + event.text };

    case "text_end":
      return state.text
        ? {
            ...append(state, { kind: "assistant", text: state.text }),
            text: "",
          }
        : state;

    case "thinking_delta":
      return { ...state, thinking: state.thinking + event.text };

    case "thinking_end":
      return state.thinking
        ? {
            ...append(state, { kind: "thinking", text: state.thinking }),
            thinking: "",
          }
        : state;

    case "tool_start":
      return {
        ...state,
        tools: [...state.tools, { id: event.id, name: event.name, input: "" }],
      };

    case "tool_input_delta":
      return {
        ...state,
        tools: state.tools.map((tool) =>
          tool.id === event.id
            ? { ...tool, input: tool.input + event.partial_json }
            : tool,
        ),
      };

    // Results arrive after the stream closes, in call order, which is when a call stops being live
    // and becomes transcript.
    case "tool_result": {
      const tool = state.tools.find((candidate) => candidate.id === event.id);
      if (!tool) return state;

      return {
        ...append(state, {
          kind: "tool",
          tool: { ...tool, result: event.content, isError: event.is_error },
        }),
        tools: state.tools.filter((candidate) => candidate.id !== event.id),
      };
    }

    case "turn_end":
      return state;

    case "notice":
      return append(state, { kind: "notice", text: event.message });

    case "error":
      return {
        ...append(state, { kind: "error", text: event.message }),
        status: "idle",
      };

    default:
      return state;
  }
}
