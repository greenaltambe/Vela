# Later: fun ideas (add to the customisation phase plan, after task 21)

## Prank mode (friends / brother)
Goal: harmless, obviously-a-joke tricks you can set up on your own Vela install (for example on a device a friend borrows).
Rules: local only, opt-in, a visible "prank" section in settings (Easter-egg gated), always one-tap reversible, never
touches accounts, never sends anything over the network, no deceptive system-level behaviour (no fake system dialogs,
no fake errors that mimic OS or Google UI, no data loss, no blocking the user from exiting). Nothing that could be
mistaken for malware.

Ideas to design later:
- Rickroll: a chosen video/rule replaces the playback target with the famous song (per-video, per-channel or random
  chance setting, "1 in N plays"), with an "ok, you got me" reveal/undo toast.
- Fake-out thumbnails: swap a thumbnail or title (local only) for a joke one, like a DeArrow-style override.
- Wrong-way UI: flipped seek bar, reversed tab order, goofy font/emoji pack Look.
- Delayed trigger: prank activates after N minutes or on the Nth app open, then self-disables.
- "Prank Look": a Look (JSON) that can be shared to a friend's phone to prank them with consent-of-install.
- Always-on escape hatch: a long-press on the logo or a Settings toggle turns every prank off instantly.

Open design question for the design conversation: which of these are fun without being annoying; default OFF.
