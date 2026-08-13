// Just want to check the issue b requirements:
// "common fine-grained updates (reply count, labels, own-post edit) rerun the full bind pipeline (resetViews, loadImage, label menus) - extend the existing PAYLOAD_REPLY_COUNT/getChangePayload path to partial-bind only affected views"

// So for (b):
// - In `getChangePayload` we have `PAYLOAD_TEAM_LEADER_CHANGED` for labels right now. Let's rename or add `PAYLOAD_LABELS_CHANGED`.
// - Actually, `reply count` doesn't use `getChangePayload`, it calls `updateReplyBadge` which calls `safeNotifyItemChanged(index, PAYLOAD_REPLY_COUNT)`. Wait, it says `extend the existing PAYLOAD_REPLY_COUNT/getChangePayload path to partial-bind only affected views`.
// Ah! In `onBindViewHolder` with payloads, if we get `PAYLOAD_EDIT_ACTION`, we rerun `loadImage`, `handleChat`, etc.
// In `PAYLOAD_TEAM_LEADER_CHANGED`, it reruns `configureEditDeleteButtons` and labels.
// Let's refine the payloads and their handling in `onBindViewHolder(..., payloads)` so that it avoids `loadImage` if it's not needed? Wait, if `message`, `isEdited`, `time`, `sharedBy`, `replyTo` changes, that is `PAYLOAD_EDIT_ACTION`.
// Is there a way we can make it more fine-grained? Or maybe they just mean "add PAYLOAD_LABELS_CHANGED instead of TEAM_LEADER_CHANGED, and don't reset views during payload updates"?
// Actually the text says: "common fine-grained updates (reply count, labels, own-post edit) rerun the full bind pipeline (resetViews, loadImage, label menus) - extend the existing PAYLOAD_REPLY_COUNT/getChangePayload path to partial-bind only affected views"
// Wait! `updateReplyBadge` does `safeNotifyItemChanged(index, PAYLOAD_REPLY_COUNT)`. And in `onBindViewHolder(..., payloads)`, `PAYLOAD_REPLY_COUNT -> updateReplyCount(holder, news, position)` is correctly handled. So what reruns the full bind pipeline?
