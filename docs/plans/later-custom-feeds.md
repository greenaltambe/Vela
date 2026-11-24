# Later: custom feeds ("a feed for any topic or mood")

> The YouTube-own-feed part is superseded by `feed-overhaul.md` section 3.3. The local rule feeds below stay a later idea.

Status: idea, after `next-home-feed-split.md`. Inspired by YouTube's "Add a custom feed" (an AI-written prompt). Not the main focus now.

## Layer 1: saved rule feeds, no AI (first version)
A named feed made of rules, shown as a pill in the Home chip row (next to All / Unwatched / ...):
- topic keywords (search queries), channels to include or exclude, duration range, live only, minimum or maximum age, hide watched.
- Results from search plus the user's subscriptions and history. Local, private, works signed out.
- Create / edit / reorder / delete in a bottom sheet; a "+" at the end of the chip row.
- Caveat: no YouTube ranking, so quality is that of the search results behind it, closer to a saved search than to YouTube's version.

## Layer 2: "Describe what you want" (optional, later)
- Turn a sentence ("cozy tech feed: dark mode, Linux setups, soft lofi", "TV comedy clips under 10 minutes") into the same rules.
- First without any service: keyword and duration extraction from the text.
- A language model only as an opt-in setting, off by default, with the disclosure that the text leaves the device (Vela is local-first).

## Open points
- Where feeds are stored and whether they go into backup and LAN sync.
- How many feeds fit in the chip row before it needs a manage screen.

## Measured: YouTube's own custom feed (live, signed-in account)
- Home carries a "Custom feed" chip and a `feedCustomizationFormViewModel` (the "Describe what you want" form, with microphone prompts).
- It also carries 20 other chips with YouTube's ranking and personalised topics (see `next-home-feed-split.md`).
- Not yet measured: what the form sends when submitted (the command and endpoint), whether the web client accepts it for this account,
  and how a created custom feed is listed afterwards. Next step: read the chip's and the form's commands from the response and test them.
- Direction: use YouTube's chips first (cheap, real ranking); try the custom feed form as an account-only feature if the endpoint works,
  and keep the local rule feeds for signed-out use.
