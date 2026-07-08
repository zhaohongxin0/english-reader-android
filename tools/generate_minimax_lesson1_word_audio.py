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
OUT_DIR = ROOT / "output" / "audio" / "minimax" / "lesson1_words"
ASSET_DIR = ROOT / "android" / "EnglishReader" / "app" / "src" / "main" / "assets" / "lessons" / "lesson1" / "words" / "audio"
API_URLS = [
    "https://api.minimaxi.com/v1/t2a_v2",
    "https://api-bj.minimaxi.com/v1/t2a_v2",
]
MODEL = "speech-2.8-hd"
ENGLISH_VOICE_ID = "English_Graceful_Lady"
CHINESE_VOICE_ID = "Chinese (Mandarin)_Crisp_Girl"

WORDS = [
    {"id": "this", "english": "this", "chinese": "这个"},
    {"id": "that", "english": "that", "chinese": "那个"},
    {"id": "these", "english": "these", "chinese": "这些"},
    {"id": "those", "english": "those", "chinese": "那些"},
    {"id": "desk", "english": "desk", "chinese": "课桌"},
    {"id": "table", "english": "table", "chinese": "桌子"},
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
            "speed": 0.96 if language_boost == "English" else 0.98,
            "vol": 1.3,
            "pitch": 0,
        },
        "audio_setting": {
            "sample_rate": 32000,
            "bitrate": 256000,
            "format": "mp3",
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


def generate_one(api_key: str, text: str, language_boost: str, voice_id: str, path: pathlib.Path) -> dict:
    last_error: Exception | None = None
    for api_url in API_URLS:
        try:
            audio, response = synthesize(api_key, text, language_boost, voice_id, api_url)
            path.write_bytes(audio)
            extra = response.get("extra_info") or {}
            return {
                "text": text,
                "language_boost": language_boost,
                "voice_id": voice_id,
                "file": path.name,
                "bytes": len(audio),
                "audio_length": extra.get("audio_length"),
                "trace_id": response.get("trace_id"),
                "api_url": api_url,
            }
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f"MiniMax generation failed for {text}: {last_error}")


def main() -> int:
    api_key = read_api_key()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "provider": "MiniMax",
        "model": MODEL,
        "english_voice_id": ENGLISH_VOICE_ID,
        "chinese_voice_id": CHINESE_VOICE_ID,
        "items": [],
    }
    for word in WORDS:
        print(f"generating {word['id']} English", flush=True)
        en_path = OUT_DIR / f"{word['id']}_en.mp3"
        en_meta = generate_one(api_key, word["english"], "English", ENGLISH_VOICE_ID, en_path)
        time.sleep(0.25)
        print(f"generating {word['id']} Chinese", flush=True)
        zh_path = OUT_DIR / f"{word['id']}_zh.mp3"
        zh_meta = generate_one(api_key, word["chinese"], "Chinese", CHINESE_VOICE_ID, zh_path)
        manifest["items"].append({
            **word,
            "english_audio": en_meta,
            "chinese_audio": zh_meta,
        })
        time.sleep(0.25)
    (OUT_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    for path in OUT_DIR.glob("*.mp3"):
        (ASSET_DIR / path.name).write_bytes(path.read_bytes())
    (ASSET_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"wrote word audio to {OUT_DIR} and {ASSET_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
