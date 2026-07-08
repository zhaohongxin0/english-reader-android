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
OUT_DIR = ROOT / "output" / "audio" / "minimax" / "lesson1_transfer"
ASSET_DIR = ROOT / "android" / "EnglishReader" / "app" / "src" / "main" / "assets" / "lessons" / "lesson1" / "transfer" / "audio"
API_URLS = [
    "https://api.minimaxi.com/v1/t2a_v2",
    "https://api-bj.minimaxi.com/v1/t2a_v2",
]
MODEL = "speech-2.8-hd"
ENGLISH_VOICE_ID = "English_Graceful_Lady"
CHINESE_VOICE_ID = "Chinese (Mandarin)_Crisp_Girl"
AUDIO_BITRATE = 256000
AUDIO_FORMAT = "mp3"

ITEMS = [
    ("01", "This is an apple.", "这是一个苹果。"),
    ("02", "That is an orange.", "那是一个橙子。"),
    ("03", "This is a book.", "这是一本书。"),
    ("04", "That is a pencil.", "那是一支铅笔。"),
    ("05", "This is a ruler.", "这是一把尺子。"),
    ("06", "That is a bag.", "那是一个书包。"),
    ("07", "This is not a table. This is a chair.", "这不是一张桌子。这是一把椅子。"),
    ("08", "That is not a desk. That is a bed.", "那不是一张课桌。那是一张床。"),
    ("09", "This is not an apple. This is an orange.", "这不是一个苹果。这是一个橙子。"),
    ("10", "That is not a book. That is a pencil.", "那不是一本书。那是一支铅笔。"),
    ("11", "These are apples.", "这些是苹果。"),
    ("12", "Those are oranges.", "那些是橙子。"),
    ("13", "These are books.", "这些是书。"),
    ("14", "Those are pencils.", "那些是铅笔。"),
    ("15", "These are rulers.", "这些是尺子。"),
    ("16", "Those are bags.", "那些是书包。"),
    ("17", "These are not tables. These are chairs.", "这些不是桌子。这些是椅子。"),
    ("18", "Those are not desks. Those are beds.", "那些不是课桌。那些是床。"),
    ("19", "These are not apples. These are oranges.", "这些不是苹果。这些是橙子。"),
    ("20", "Those are not books. Those are pencils.", "那些不是书。那些是铅笔。"),
    ("21", "This is an egg.", "这是一个鸡蛋。"),
    ("22", "That is an umbrella.", "那是一把伞。"),
    ("23", "These are eggs.", "这些是鸡蛋。"),
    ("24", "Those are umbrellas.", "那些是伞。"),
    ("25", "That's an orange.", "那是一个橙子。"),
    ("26", "That's a pencil.", "那是一支铅笔。"),
    ("27", "That's a bag.", "那是一个书包。"),
    ("28", "That's not a desk. That's a bed.", "那不是一张课桌。那是一张床。"),
    ("29", "That's not a book. That's a pencil.", "那不是一本书。那是一支铅笔。"),
    ("30", "That's an umbrella.", "那是一把伞。"),
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


def synthesize(api_key: str, text: str, language_boost: str, voice_id: str, api_url: str) -> tuple[bytes, dict]:
    payload = {
        "model": MODEL,
        "text": text,
        "stream": False,
        "language_boost": language_boost,
        "output_format": "hex",
        "voice_setting": {
            "voice_id": voice_id,
            "speed": 0.98 if language_boost == "English" else 1.04,
            "vol": 1.5 if language_boost == "English" else 5.0,
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


def generate_one(api_key: str, text: str, language_boost: str, voice_id: str, file_name: str) -> dict:
    last_error: Exception | None = None
    for api_url in API_URLS:
        try:
            audio, response = synthesize(api_key, text, language_boost, voice_id, api_url)
            out_path = OUT_DIR / file_name
            asset_path = ASSET_DIR / file_name
            out_path.write_bytes(audio)
            asset_path.write_bytes(audio)
            extra = response.get("extra_info") or {}
            return {
                "text": text,
                "language_boost": language_boost,
                "voice_id": voice_id,
                "file": file_name,
                "bytes": len(audio),
                "audio_length": extra.get("audio_length"),
                "usage_characters": extra.get("usage_characters"),
                "trace_id": response.get("trace_id"),
                "api_url": api_url,
            }
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f"MiniMax generation failed for {file_name}: {last_error}")


def main() -> int:
    api_key = read_api_key()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "provider": "MiniMax",
        "model": MODEL,
        "english_voice_id": ENGLISH_VOICE_ID,
        "chinese_voice_id": CHINESE_VOICE_ID,
        "audio_setting": {
            "sample_rate": 32000,
            "bitrate": AUDIO_BITRATE,
            "format": AUDIO_FORMAT,
            "channel": 1,
        },
        "items": [],
    }
    for item_id, english, chinese in ITEMS:
        print(f"generating {item_id} English: {english}", flush=True)
        en = generate_one(api_key, english, "English", ENGLISH_VOICE_ID, f"{item_id}_en.mp3")
        time.sleep(0.25)
        print(f"generating {item_id} Chinese: {chinese}", flush=True)
        zh = generate_one(api_key, chinese, "Chinese", CHINESE_VOICE_ID, f"{item_id}_zh.mp3")
        manifest["items"].append(
            {
                "id": item_id,
                "english": english,
                "chinese": chinese,
                "english_audio": en,
                "chinese_audio": zh,
            }
        )
        time.sleep(0.25)
    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2)
    (OUT_DIR / "manifest.json").write_text(manifest_text, encoding="utf-8")
    (ASSET_DIR / "manifest.json").write_text(manifest_text, encoding="utf-8")
    print(f"wrote {len(ITEMS)} transfer items to {ASSET_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
