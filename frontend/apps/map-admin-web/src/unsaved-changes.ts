export const UNSAVED_CHANGES_CONFIRMATION =
  "当前版本有未保存修改，确认放弃并继续？";

export type ConfirmDiscard = (message: string) => boolean;

export function confirmDiscardUnsavedChanges(
  dirty: boolean,
  confirmDiscard: ConfirmDiscard,
): boolean {
  return !dirty || confirmDiscard(UNSAVED_CHANGES_CONFIRMATION);
}

export function createUnsavedChangesBeforeUnloadHandler(
  isDirty: () => boolean,
): (event: BeforeUnloadEvent) => void {
  return (event) => {
    if (!isDirty()) {
      return;
    }
    event.preventDefault();
    event.returnValue = "";
  };
}
