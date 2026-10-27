#!/usr/bin/env python3
"""Capture REAL yt-dlp output as test fixtures, trimmed and scrubbed.

    python3 extractor-ytdlp/scripts/capture_fixtures.py [path/to/yt-dlp]

Writes into src/test/resources/fixtures/:
  video.json           a normal video (rich format list, HLS + storyboards + DASH-style https formats trimmed)
  short.json           a Short, requested through its /watch URL, the way the extractor asks for it
  audio_drc.json       a video that offers `-drc` audio variants
  audio_multilang.json a video with many dubbed audio tracks (`251-0` ... `251-23`, one marked default)
  flat_search.json     --flat-playlist search results
  flat_channel.json    --flat-playlist channel /videos
  stderr_*.txt         what yt-dlp prints for videos that cannot be played (the file name says which case)

Review `git diff` before committing: the scrubber removes the client IP and signature material from URLs but
cannot know every field a future yt-dlp adds. The script asserts that the IP it was run from does not appear.
"""
import json, re, subprocess, sys, urllib.request
from pathlib import Path

binary = sys.argv[1] if len(sys.argv) > 1 else "yt-dlp"
out = Path(__file__).resolve().parent.parent / "src/test/resources/fixtures"
WATCH = "https://www.youtube.com/watch?v="
SCRUB = re.compile(r"([?&/])(ip|ei|sig|lsig|pot|n|vprv|mt|id|ipbits|rms|met|mh|mm|mn|ms)([=/])[^&/]*")
VERSION = subprocess.run([binary, "--version"], capture_output=True, text=True).stdout.strip()


def ytdlp(*args, check=True):
    return subprocess.run([binary, *args], check=check, capture_output=True, text=True)


def run(*args):
    return json.loads(ytdlp("--dump-single-json", "--no-warnings", *args).stdout)


def scrub(value):
    if isinstance(value, str):
        return SCRUB.sub(lambda m: f"{m[1]}{m[2]}{m[3]}x", value) if value.startswith("http") else value
    if isinstance(value, list):
        return [scrub(v) for v in value]
    if isinstance(value, dict):
        return {k: scrub(v) for k, v in value.items()}
    return value


def write(name, data):
    text = json.dumps({"_fixture": f"captured from yt-dlp {VERSION}", **scrub(data)}, indent=1, ensure_ascii=False) + "\n"
    (out / name).write_text(text)
    print("wrote", name, len(text), "bytes")


def trim_video(video, keep_formats):
    for drop in ("automatic_captions", "subtitles", "heatmap", "requested_formats", "requested_downloads", "formats_requested"):
        video.pop(drop, None)
    video["description"] = (video.get("description") or "")[:200]
    video["tags"] = (video.get("tags") or [])[:5]
    video["thumbnails"] = (video.get("thumbnails") or [])[-2:]
    video["formats"] = [f for f in video["formats"] if f.get("format_id") in keep_formats]
    return video


# One of each shape: storyboard, HLS audio-only (acodec is null!), https audio, https video-only, HLS video-only.
write("video.json", trim_video(run("--no-playlist", WATCH + "dQw4w9WgXcQ"),
                               {"sb0", "233", "139", "140", "251", "249", "270", "137", "248", "401"}))

shorts_tab = run("--flat-playlist", "--playlist-end", "1", "https://www.youtube.com/@YouTube/shorts")
short_id = shorts_tab["entries"][0]["id"]
write("short.json", trim_video(run("--no-playlist", WATCH + short_id), {"140", "251", "137", "248"}))

write("audio_drc.json", trim_video(run("--no-playlist", WATCH + "jNQXAC9IVRw"), {"140", "140-drc", "251", "251-drc"}))
write("audio_multilang.json", trim_video(run("--no-playlist", WATCH + "0e3GPea1Tyg"), {"251-0", "251-4", "251-23", "140-23"}))

search = run("--flat-playlist", "--playlist-end", "5", "ytsearch5:rick astley")
search["entries"] = search["entries"][:5]
write("flat_search.json", search)

channel = run("--flat-playlist", "--playlist-end", "3", "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw/videos")
channel["entries"] = channel["entries"][:3]
write("flat_channel.json", channel)

# Failure cases: stdout is "null" and stderr carries the only information. Private and age-gated videos cannot be
# reproduced without a specific account or video, so only these are captured.
for name, url in {
    "stderr_unavailable.txt": WATCH + "aaaaaaaaaaa",
    "stderr_terminated.txt": WATCH + "Tuw8hxrFBH8",
    "stderr_truncated_id.txt": WATCH + "abc",
    "stderr_not_a_url.txt": "not-a-url",
    "stderr_channel_missing.txt": "https://www.youtube.com/channel/UCxxxxxxxxxxxxxxxxxxxxxx/videos",
}.items():
    result = ytdlp("--dump-single-json", "--no-warnings", "--no-playlist", url, check=False)
    assert result.returncode != 0, f"{url} unexpectedly succeeded"
    (out / name).write_text(result.stderr)
    print("wrote", name, repr(result.stderr.strip()[:100]), "exit", result.returncode)

# The scrubber must have removed the address yt-dlp saw us as.
try:
    my_ip = urllib.request.urlopen("https://api.ipify.org", timeout=10).read().decode()
    leaks = [p.name for p in out.glob("*") if p.is_file() and my_ip in p.read_text()]
    assert not leaks, f"client IP {my_ip} still present in {leaks}"
    print("no client IP leak")
except OSError as e:
    print("could not check for IP leaks:", e)
