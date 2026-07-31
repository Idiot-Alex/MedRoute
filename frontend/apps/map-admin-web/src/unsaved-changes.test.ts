import { describe, expect, it, vi } from "vitest";
import {
  UNSAVED_CHANGES_CONFIRMATION,
  confirmDiscardUnsavedChanges,
  createUnsavedChangesBeforeUnloadHandler,
} from "./unsaved-changes";

describe("confirmDiscardUnsavedChanges", () => {
  it("continues without prompting when the workspace is clean", () => {
    const confirmDiscard = vi.fn(() => false);

    expect(confirmDiscardUnsavedChanges(false, confirmDiscard)).toBe(
      true,
    );
    expect(confirmDiscard).not.toHaveBeenCalled();
  });

  it("uses the same confirmation and respects rejection for dirty workspaces", () => {
    const confirmDiscard = vi.fn(() => false);

    expect(confirmDiscardUnsavedChanges(true, confirmDiscard)).toBe(
      false,
    );
    expect(confirmDiscard).toHaveBeenCalledWith(
      UNSAVED_CHANGES_CONFIRMATION,
    );
  });

  it("continues when discarding dirty changes is confirmed", () => {
    expect(confirmDiscardUnsavedChanges(true, () => true)).toBe(true);
  });
});

describe("createUnsavedChangesBeforeUnloadHandler", () => {
  it("does not block navigation when the workspace is clean", () => {
    const event = beforeUnloadEvent();
    const handler = createUnsavedChangesBeforeUnloadHandler(
      () => false,
    );

    handler(event.value);

    expect(event.preventDefault).not.toHaveBeenCalled();
    expect(event.value.returnValue).toBe("unchanged");
  });

  it("requests the browser native warning when the workspace is dirty", () => {
    const event = beforeUnloadEvent();
    const handler = createUnsavedChangesBeforeUnloadHandler(() => true);

    handler(event.value);

    expect(event.preventDefault).toHaveBeenCalledOnce();
    expect(event.value.returnValue).toBe("");
  });
});

function beforeUnloadEvent(): {
  value: BeforeUnloadEvent;
  preventDefault: ReturnType<typeof vi.fn>;
} {
  const preventDefault = vi.fn();
  const value = {
    preventDefault,
    returnValue: "unchanged",
  } as unknown as BeforeUnloadEvent;
  return { value, preventDefault };
}
