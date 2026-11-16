/**
 * Tracks whether the entry editor holds unsaved writing, so anything that would throw it away
 * (opening another day, closing the editor, signing out, leaving the page) can ask first.
 */
let unsaved = false;

export function setUnsaved(value: boolean) {
  unsaved = value;
}

export function hasUnsaved(): boolean {
  return unsaved;
}

export const DISCARD_PROMPT = "You have unsaved changes to today's entry. Discard them?";

/** True when it is fine to continue: nothing unsaved, or the user agreed to discard it. */
export function confirmDiscard(): boolean {
  if (!unsaved || window.confirm(DISCARD_PROMPT)) {
    unsaved = false;
    return true;
  }
  return false;
}
