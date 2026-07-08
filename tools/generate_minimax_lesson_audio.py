#!/usr/bin/env python3
import binascii
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "output" / "audio" / "minimax" / "lesson1"
METADATA_PATH = OUT_DIR / "manifest.json"

API_URLS = [
    "https://api.minimaxi.com/v1/t2a_v2",
    "https://api-bj.minimaxi.com/v1/t2a_v2",
]
MODEL = "speech-2.8-hd"
VOICE_ID = "English_Graceful_Lady"
VOICE_SPEED = float(os.environ.get("MINIMAX_VOICE_SPEED", "0.98"))
VOICE_VOLUME = float(os.environ.get("MINIMAX_VOICE_VOLUME", "1.3"))
AUDIO_BITRATE = int(os.environ.get("MINIMAX_BITRATE", "256000"))
AUDIO_FORMAT = os.environ.get("MINIMAX_AUDIO_FORMAT", "mp3")

SENTENCES = [
    "This is a desk.",
    "That's a table.",
    "These are desks.",
    "Those are tables.",
    "This is not a table.",
    "That is not a desk.",
    "These are not tables.",
    "Those are not desks.",
]


def read_api_key() -> str:
    api_key = os.environ.get("MINIMAX_API_KEY", "").strip()
    if api_key:
        return api_key
    key_note = os.environ.get("MINIMAX_KEY_NOTE", "").strip()
    if not key_note:
        raise RuntimeError("Set MINIMAX_API_KEY or MINIMAX_KEY_NOTE before generating audio")
    text = pathlib.Path(key_note).read_text(encoding="utf-8")
    ordinary = re.search(
        r"(?im)^\s*minimax\s+api\s+key[^\n]*\n\s*(sk-api-[^\s]+)",
        text,
    )
    if not ordinary:
        raise RuntimeError("MiniMax ordinary API key not found in knowledge-base note")
    return ordinary.group(1).strip()


def synthesize(api_key: str, text: str, api_url: str) -> tuple[bytes, dict]:
    payload = {
        "model": MODEL,
        "text": text,
        "stream": False,
        "language_boost": "English",
        "output_format": "hex",
        "voice_setting": {
            "voice_id": VOICE_ID,
            "speed": VOICE_SPEED,
            "vol": VOICE_VOLUME,
            "pitch": 0,
        },
        "audio_setting": {
            "sample_rate": 32000,
            "bitrate": AUDIO_BITRATE,
            "format": AUDIO_FORMAT,
            "channel": 1,
        },
    }
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        api_url,
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            raw = resp.read()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"MiniMax HTTP {exc.code}: {detail}") from exc
    data = json.loads(raw.decode("utf-8"))
    base = data.get("base_resp") or {}
    if base.get("status_code") not in (0, None):
        raise RuntimeError(f"MiniMax error: {base}")
    audio_hex = (data.get("data") or {}).get("audio")
    if not audio_hex:
        raise RuntimeError(f"MiniMax response did not include audio: {data}")
    return binascii.unhexlify(audio_hex), data


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    api_key = read_api_key()
    manifest = {
        "provider": "MiniMax",
        "model": MODEL,
        "voice_id": VOICE_ID,
        "voice_speed": VOICE_SPEED,
        "voice_volume": VOICE_VOLUME,
        "language_boost": "English",
        "audio_setting": {
            "sample_rate": 32000,
            "bitrate": AUDIO_BITRATE,
            "format": AUDIO_FORMAT,
            "channel": 1,
        },
        "items": [],
    }

    active_api_url: str | None = None
    for idx, sentence in enumerate(SENTENCES, start=1):
        path = OUT_DIR / f"{idx:02d}.{AUDIO_FORMAT}"
        print(f"generating {idx:02d}: {sentence}", flush=True)
        last_error: Exception | None = None
        api_attempts = [active_api_url] if active_api_url else API_URLS
        for api_url in api_attempts:
            if api_url is None:
                continue
            try:
                audio, response = synthesize(api_key, sentence, api_url)
                active_api_url = api_url
                manifest["api_url"] = api_url
                manifest["credential_kind"] = "ordinary_api_key"
                break
            except Exception as exc:
                last_error = exc
        else:
            raise RuntimeError(f"MiniMax generation failed for {idx:02d}: {last_error}")
        path.write_bytes(audio)
        extra = response.get("extra_info") or {}
        manifest["items"].append(
            {
                "index": idx,
                "text": sentence,
                "file": path.name,
                "bytes": len(audio),
                "audio_length": extra.get("audio_length"),
                "usage_characters": extra.get("usage_characters"),
                "trace_id": response.get("trace_id"),
            }
        )
        time.sleep(0.35)

    METADATA_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {len(SENTENCES)} audio files to {OUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
