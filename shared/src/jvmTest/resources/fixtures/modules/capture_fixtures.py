#!/usr/bin/env python3
"""Captures real responses from the three third-party APIs for the offline module tests.

    python3 capture_fixtures.py VIDEO_ID [VIDEO_ID ...]

For each id this writes (next to this script, nothing is edited or scrubbed; the APIs return public data):
  sb_skipsegments_<id>.json      SponsorBlock privacy lookup (hash prefix; the answer includes other videos sharing the prefix)
  ryd_votes_<id>.json            Return YouTube Dislike
  dearrow_branding_<prefix>.json DeArrow privacy lookup (only written for the first id that has a submitted title)
  <name>.status                  the HTTP status of each request (a 404 is a normal answer for "nothing here")

The ids used for the committed fixtures were taken from the RSS feed of a sponsor-heavy channel on the day of capture;
sponsor and title data change over time, so re-capturing may change what the tests see. Tests only assert on shape and
on the matching/selection rules, not on specific counts that change.
"""
import hashlib, json, sys, urllib.request, urllib.error, pathlib

OUT = pathlib.Path(__file__).parent
UA = {"User-Agent": "Vela-fixture-capture (personal use)"}
CATS = "%5B%22sponsor%22%2C%22selfpromo%22%2C%22interaction%22%2C%22intro%22%2C%22outro%22%2C%22preview%22%2C%22music_offtopic%22%2C%22filler%22%2C%22poi_highlight%22%2C%22exclusive_access%22%5D"
TYPES = "%5B%22skip%22%2C%22mute%22%2C%22poi%22%5D"


def get(url, name):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
            body, status = r.read().decode(), r.status
    except urllib.error.HTTPError as e:
        body, status = e.read().decode(), e.code
    (OUT / f"{name}.status").write_text(str(status))
    return status, body


def main(ids):
    wrote_branding = False
    for vid in ids:
        prefix = hashlib.sha256(vid.encode()).hexdigest()[:4]
        st, body = get(f"https://sponsor.ajay.app/api/skipSegments/{prefix}?categories={CATS}&actionTypes={TYPES}", f"sb_skipsegments_{vid}")
        if st == 200:
            (OUT / f"sb_skipsegments_{vid}.json").write_text(body)
        st, body = get(f"https://returnyoutubedislikeapi.com/votes?videoId={vid}", f"ryd_votes_{vid}")
        if st == 200:
            (OUT / f"ryd_votes_{vid}.json").write_text(body)
        if not wrote_branding:
            st, body = get(f"https://sponsor.ajay.app/api/branding/{prefix}", f"dearrow_branding_{prefix}")
            if st == 200 and json.loads(body).get(vid, {}).get("titles"):
                (OUT / f"dearrow_branding_{prefix}.json").write_text(body)
                wrote_branding = True
                print("DeArrow branding written for", vid, "prefix", prefix)
        print(vid, "done")


if __name__ == "__main__":
    main(sys.argv[1:] or sys.exit(__doc__))
