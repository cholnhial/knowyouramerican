#!/usr/bin/env python3
"""Downloads the background photos from Wikimedia Commons and writes backgrounds.json.

Each image is fetched at 3840px wide, cover-cropped to 2560x1600 and saved as WebP
(src/main/resources/static/img/bg/<slug>.webp). Credits and licences come from the Commons
API so the on-page caption can attribute them (CC BY / BY-SA require it).

Usage:  python3 scripts/fetch-backgrounds.py          (needs ImageMagick `convert` + `cwebp`)
Add a place: append to PLACES with an exact Commons file title, then re-run.
"""
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src/main/resources/static/img/bg")
CATALOG = os.path.join(ROOT, "src/main/resources/backgrounds.json")
UA = {"User-Agent": "knowyouramerican/1.0 (background fetcher; https://kya.chol.dev)"}

# slug, title, location, Commons file
PLACES = [
    ("grand-canyon", "Grand Canyon", "South Rim, Arizona", "File:Grand Canyon South Rim at Sunset.jpg"),
    ("statue-of-liberty", "Statue of Liberty", "Liberty Island, New York", "File:Statue of Liberty and a sightseeing boat, Liberty Island, New York.jpg"),
    ("golden-gate-bridge", "Golden Gate Bridge", "San Francisco, California", "File:Golden Gate Bridge as seen from Marshall’s Beach, March 2018.jpg"),
    ("mount-rushmore", "Mount Rushmore", "Keystone, South Dakota", "File:Mount Rushmore detail view.jpg"),
    ("yosemite", "Yosemite Valley", "Tunnel View, California", "File:Tunnel View, Yosemite Valley, Yosemite NP - Diliff.jpg"),
    ("grand-prismatic", "Grand Prismatic Spring", "Yellowstone, Wyoming", "File:Yellowstone National Park (WY, USA), Grand Prismatic Spring -- 2022 -- 2519.jpg"),
    ("lincoln-memorial", "Lincoln Memorial", "Washington, D.C.", "File:Lincoln Memorial east side.JPG"),
    ("independence-hall", "Independence Hall", "Philadelphia, Pennsylvania", "File:Independence Hall, 2016 2.jpg"),
    ("monument-valley", "Monument Valley", "Utah–Arizona border", "File:Forrest Gump Point Monument Valley November 2018 001.jpg"),
    ("niagara-falls", "Niagara Falls", "New York", "File:Maid of the Mist VII approaching the Horseshoe Falls, West view 20170418 1.jpg"),
    ("delicate-arch", "Delicate Arch", "Arches National Park, Utah", "File:ArchesNationalParkDelicateArch.jpg"),
    ("gateway-arch", "Gateway Arch", "St. Louis, Missouri", "File:St Louis night expblend.jpg"),
    ("brooklyn-bridge", "Brooklyn Bridge", "New York City", "File:Brooklyn Bridge August 2017.jpg"),
    ("mesa-verde", "Cliff Palace", "Mesa Verde, Colorado", "File:Mesa Verde National Park Cliff Palace 2006 09 12.jpg"),
    ("hoover-dam", "Hoover Dam", "Black Canyon, Nevada", "File:Approach road, switchyards and canyon near Hoover Dam, panorama, Nevada, United States.jpg"),
    ("the-alamo", "The Alamo", "San Antonio, Texas", "File:Alamo pano.jpg"),
    ("great-smoky-mountains", "Great Smoky Mountains", "Tennessee–North Carolina", "File:Meadow in the Smoky Mountains (41923p).jpg"),
    ("horseshoe-bend", "Horseshoe Bend", "Page, Arizona", "File:Horseshoe Bend Arizona dllu.jpg"),
]


def api(params):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({**params, "format": "json"})
    for attempt in range(6):
        try:
            time.sleep(1.5)
            return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            time.sleep(15 * (attempt + 1))
    raise RuntimeError("Commons API rate limit")


def plain(text):
    """Commons metadata is HTML; keep the readable name."""
    text = re.sub(r"<[^>]+>", "", text or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    catalog = []
    for slug, title, location, file in PLACES:
        info = api({"action": "query", "titles": file, "prop": "imageinfo",
                    "iiprop": "url|extmetadata|size", "iiurlwidth": 3840})
        page = next(iter(info["query"]["pages"].values()))
        ii = page["imageinfo"][0]
        meta = ii["extmetadata"]
        artist = plain(meta.get("Artist", {}).get("value")) or "Wikimedia Commons"
        licence = meta.get("LicenseShortName", {}).get("value", "")
        target = os.path.join(OUT_DIR, slug + ".webp")
        if not os.path.exists(target):
            with tempfile.TemporaryDirectory() as tmp:
                src = os.path.join(tmp, "src.jpg")
                crop = os.path.join(tmp, "crop.jpg")
                time.sleep(1)
                with urllib.request.urlopen(urllib.request.Request(ii["thumburl"], headers=UA)) as r:
                    open(src, "wb").write(r.read())
                subprocess.run(["convert", src, "-resize", "2560x1600^", "-gravity", "center",
                                "-extent", "2560x1600", "-strip", crop], check=True)
                subprocess.run(["cwebp", "-quiet", "-size", "450000", "-pass", "6", crop, "-o", target], check=True)
        catalog.append({
            "slug": slug,
            "title": title,
            "location": location,
            "credit": artist[:80],
            "license": licence,
            "sourceUrl": ii["descriptionurl"],
        })
        print(f"{slug:24} {os.path.getsize(target) // 1024:5d} KB  {licence:14} {artist[:40]}", file=sys.stderr)
    with open(CATALOG, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {len(catalog)} backgrounds → {CATALOG}", file=sys.stderr)


if __name__ == "__main__":
    main()
