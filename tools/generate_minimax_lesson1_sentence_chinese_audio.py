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
OUT_DIR = ROOT / "output" / "audio" / "minimax" / "lesson1_sentence_zh"
ASSET_DIR = ROOT / "android" / "EnglishReader" / "app" / "src" / "main" / "assets" / "lessons" / "lesson1" / "audio"
API_URLS = [
    "https://api.minimaxi.com/v1/t2a_v2",
    "https://api-bj.minimaxi.com/v1/t2a_v2",
]
MODEL = "speech-2.8-hd"
VOICE_ID = "Chinese (Mandarin)_Crisp_Girl"
VOICE_SPEED = float(os.environ.get("MINIMAX_CHINESE_SENTENCE_SPEED", "1.04"))
VOICE_VOLUME = float(os.environ.get("MINIMAX_CHINESE_SENTENCE_VOLUME", "5.0"))
# MiniMax t2a_v2 rejects 320000 for MP3; 256 kbps is the highest accepted high-quality setting.
AUDIO_BITRATE = int(os.environ.get("MINIMAX_BITRATE", "256000"))
AUDIO_FORMAT = os.environ.get("MINIMAX_AUDIO_FORMAT", "mp3")

SENTENCES = [
    "这是一张课桌。",
    "那是一张桌子。",
    "这些是课桌。",
    "那些是桌子。",
    "这不是一张桌子。",
    "那不是一张课桌。",
    "这些不是桌子。",
    "那些不是课桌。",
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
        "language_boost": "Chinese",
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
    req = urllib.request.Request(
        api_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"MiniMax HTTP {exc.code}: {detail}") from exc
    base = data.get("base_resp") or {}
    if base.get("status_code") not in (0, None):
        raise RuntimeError(f"MiniMax error: {base}")
    audio_hex = (data.get("data") or {}).get("audio")
    if not audio_hex:
        raise RuntimeError(f"MiniMax response did not include audio: {data}")
    return binascii.unhexlify(audio_hex), data


def generate_one(api_key: str, index: int, text: str) -> dict:
    last_error: Exception | None = None
    for api_url in API_URLS:
        try:
            audio, response = synthesize(api_key, text, api_url)
            file_name = f"{index:02d}_zh.{AUDIO_FORMAT}"
            out_path = OUT_DIR / file_name
            asset_path = ASSET_DIR / file_name
            out_path.write_bytes(audio)
            asset_path.write_bytes(audio)
            extra = response.get("extra_info") or {}
            return {
                "index": index,
                "text": text,
                "file": file_name,
                "bytes": len(audio),
                "audio_length": extra.get("audio_length"),
                "usage_characters": extra.get("usage_characters"),
                "trace_id": response.get("trace_id"),
                "api_url": api_url,
            }
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f"MiniMax generation failed for {index:02d}: {last_error}")


def main() -> int:
    api_key = read_api_key()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "provider": "MiniMax",
        "model": MODEL,
        "voice_id": VOICE_ID,
        "voice_speed": VOICE_SPEED,
        "voice_volume": VOICE_VOLUME,
        "language_boost": "Chinese",
        "audio_setting": {
            "sample_rate": 32000,
            "bitrate": AUDIO_BITRATE,
            "format": AUDIO_FORMAT,
            "channel": 1,
        },
        "items": [],
    }
    for index, sentence in enumerate(SENTENCES, start=1):
        print(f"generating {index:02d}: {sentence}", flush=True)
        manifest["items"].append(generate_one(api_key, index, sentence))
        time.sleep(0.35)
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2)
    (OUT_DIR / "manifest.json").write_text(manifest_text, encoding="utf-8")
    (ASSET_DIR / "manifest_zh.json").write_text(manifest_text, encoding="utf-8")
    print(f"wrote {len(SENTENCES)} Chinese sentence audio files to {ASSET_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
