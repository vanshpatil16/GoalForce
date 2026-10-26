# Attention Path Graph Design

## Goal

Help a user see which app navigation paths tend to pull them away from an active intention, then let them protect a repeatedly distracting path. The first version uses only information Android exposes through the existing AccessibilityService and locally recorded focus-session events.

## Current project behavior

- `DriftAccessibilityService` observes app/window events and short-form viewer screens.
- `ContentScanner` recognizes Reels, Shorts, and TikTok by exposed accessibility view IDs. It does not inspect captions or video pixels.
- `ForegroundEvent` stores app opens and content-block events, with session IDs and timestamps.
- The app already has cover and complete-block modes. Cover mode masks a detected short-form video while keeping navigation outside the viewer usable.
- Room currently uses destructive fallback migration, so adding a Room table risks clearing existing local data.

## Proposed first version

### Observation and graph model

Extend accessibility observation to include view clicks, scrolls, and screen-content changes. Convert events into coarse, stable state nodes such as `Instagram / feed`, `Instagram / inbox`, and `Instagram / Reels`; connect consecutive nodes with edges that count how often a transition was observed. Use package name plus a small allowlist of app-provided view IDs and accessible labels to infer states. Unknown screens remain `unknown`; do not guess a label from package names or launcher screens.

Visible text may be read transiently on-device to identify a coarse content category or compare words with the active goal. Store only the resulting category/relevance bucket, never raw captions, usernames, message text, or screen contents. No screenshots, OCR, or external AI calls are included in this version.

Store a bounded graph locally, with counts and timestamps rather than a raw interaction transcript. Keep a small cap on distinct nodes/edges and prune old or low-count edges. Prefer private app storage outside Room to avoid triggering the current destructive Room migration fallback.

### Risk and recommendations

Correlate graph paths with existing session signals such as repeated short-form views, app exits/re-entries, and session overruns. Call a path “associated with distraction” only after it appears across multiple sessions; show the sample count and avoid causal claims. Until there is enough evidence, show the route as observed with “not enough sessions yet.”

The graph should make a high-risk short-form path selectable for protection. Selection reuses the user's existing cover or complete-block preference and applies only to supported viewer states. When the path enters a detected Reels/Shorts/TikTok viewer, invoke the existing block behavior. Do not automatically cover a navigation button or issue a global Back action based on a weak prediction: third-party labels and navigation controls are unstable, and a false positive could disrupt normal app use.

### UI

Add a focused “Attention paths” view, reachable from analytics/coach, showing the observed state-and-transition graph, transition counts, and distraction association/sample size. Let the user select a supported short-form viewer node as a block target. Explain unsupported or unknown nodes without offering a control that cannot be enforced.

## Privacy and reliability

- All state classification and graph scoring run on-device.
- Never persist raw accessible text, usernames, message text, or screenshots.
- Do not send accessibility content to Groq or the news feed.
- Accessibility trees vary across app versions and may omit content. Mark state detection as observed only when a known marker is present; otherwise use unknown.
- The graph reports correlation, not proof that content caused an overrun.
- Enforce limits on graph size and age so long-term storage remains bounded.

## Acceptance criteria

1. With Accessibility enabled, entering a supported screen and moving between supported screens records graph edges tied to the active session where applicable.
2. A path graph displays edge counts and clearly distinguishes insufficient data from observed distraction association.
3. Raw accessible text, screenshots, and video frames are absent from persistent graph data and network payloads.
4. A user can select a supported short-form viewer node for protection; the selected node uses the existing cover/complete-block mode and preserves existing DM exemptions.
5. Unknown states and unsupported apps do not trigger automatic blocking.
6. The implementation avoids Room schema changes while destructive fallback migration remains configured.

## Out of scope

- Capturing or classifying video frames or audio.
- Uploading screenshots, captions, or interaction traces.
- Claiming semantic understanding of a Reel when its content is not exposed to accessibility.
- Blocking arbitrary third-party UI controls based only on a learned statistical association.
- Social or account-level graph sharing.
