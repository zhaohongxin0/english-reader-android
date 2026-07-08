#!/usr/bin/env python3
import binascii
import json
import os
import pathlib
import re
import shutil
import sys
import time
import urllib.error
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET_ROOT = ROOT / "android" / "EnglishReader" / "app" / "src" / "main" / "assets"
LESSON_DIR = ASSET_ROOT / "lessons" / "lesson2"
OUT_DIR = ROOT / "output" / "audio" / "minimax" / "lesson2"
IMAGE_SOURCE = ASSET_ROOT / "lessons" / "lesson1" / "desk_table_phone_portrait_v1.jpg"
IMAGE_NAME = "desk_table_phone_portrait_v1.jpg"

API_URLS = [
    "https://api.minimaxi.com/v1/t2a_v2",
    "https://api-bj.minimaxi.com/v1/t2a_v2",
]
MODEL = "speech-2.8-hd"
ENGLISH_VOICE_ID = "English_Graceful_Lady"
CHINESE_VOICE_ID = "Chinese (Mandarin)_Crisp_Girl"
AUDIO_BITRATE = 256000
AUDIO_FORMAT = "mp3"

WORDS = [
    {"id": "is", "english": "is", "chinese": "是"},
    {"id": "are", "english": "are", "chinese": "是（复数）"},
    {"id": "yes", "english": "yes", "chinese": "是的"},
    {"id": "no", "english": "no", "chinese": "不"},
    {"id": "it", "english": "it", "chinese": "它"},
    {"id": "they", "english": "they", "chinese": "它们"},
]

ITEMS = [
    ("01", "Is this a desk? Yes, it is.", "这是一张课桌吗？是的，它是。", [0.0, 0.0, 1.0, 0.125]),
    ("02", "Is that a table? Yes, it is.", "那是一张桌子吗？是的，它是。", [0.0, 0.125, 1.0, 0.125]),
    ("03", "Are these desks? Yes, they are.", "这些是课桌吗？是的，它们是。", [0.0, 0.25, 1.0, 0.125]),
    ("04", "Are those tables? Yes, they are.", "那些是桌子吗？是的，它们是。", [0.0, 0.375, 1.0, 0.125]),
    ("05", "Is this a table? No, it isn't. It's a desk.", "这是一张桌子吗？不，它不是。它是一张课桌。", [0.0, 0.5, 1.0, 0.125]),
    ("06", "Is that a desk? No, it isn't. It's a table.", "那是一张课桌吗？不，它不是。它是一张桌子。", [0.0, 0.625, 1.0, 0.125]),
    ("07", "Are these tables? No, they aren't. They're desks.", "这些是桌子吗？不，它们不是。它们是课桌。", [0.0, 0.75, 1.0, 0.125]),
    ("08", "Are those desks? No, they aren't. They're tables.", "那些是课桌吗？不，它们不是。它们是桌子。", [0.0, 0.875, 1.0, 0.125]),
]

TRANSFER_ITEMS = [
    ("01", "Is this an apple? Yes, it is.", "这是一个苹果吗？是的，它是。"),
    ("02", "Is that an orange? Yes, it is.", "那是一个橙子吗？是的，它是。"),
    ("03", "Is this a book? Yes, it is.", "这是一本书吗？是的，它是。"),
    ("04", "Is that a pencil? Yes, it is.", "那是一支铅笔吗？是的，它是。"),
    ("05", "Is this a ruler? Yes, it is.", "这是一把尺子吗？是的，它是。"),
    ("06", "Is that a bag? Yes, it is.", "那是一个书包吗？是的，它是。"),
    ("07", "Is this a table? No, it isn't. It's a chair.", "这是一张桌子吗？不，它不是。它是一把椅子。"),
    ("08", "Is that a desk? No, it isn't. It's a bed.", "那是一张课桌吗？不，它不是。它是一张床。"),
    ("09", "Is this an apple? No, it isn't. It's an orange.", "这是一个苹果吗？不，它不是。它是一个橙子。"),
    ("10", "Is that a book? No, it isn't. It's a pencil.", "那是一本书吗？不，它不是。它是一支铅笔。"),
    ("11", "Are these apples? Yes, they are.", "这些是苹果吗？是的，它们是。"),
    ("12", "Are those oranges? Yes, they are.", "那些是橙子吗？是的，它们是。"),
    ("13", "Are these books? Yes, they are.", "这些是书吗？是的，它们是。"),
    ("14", "Are those pencils? Yes, they are.", "那些是铅笔吗？是的，它们是。"),
    ("15", "Are these rulers? Yes, they are.", "这些是尺子吗？是的，它们是。"),
    ("16", "Are those bags? Yes, they are.", "那些是书包吗？是的，它们是。"),
    ("17", "Are these tables? No, they aren't. They're chairs.", "这些是桌子吗？不，它们不是。它们是椅子。"),
    ("18", "Are those desks? No, they aren't. They're beds.", "那些是课桌吗？不，它们不是。它们是床。"),
    ("19", "Are these apples? No, they aren't. They're oranges.", "这些是苹果吗？不，它们不是。它们是橙子。"),
    ("20", "Are those books? No, they aren't. They're pencils.", "那些是书吗？不，它们不是。它们是铅笔。"),
    ("21", "Is this an egg? Yes, it is.", "这是一个鸡蛋吗？是的，它是。"),
    ("22", "Is that an umbrella? Yes, it is.", "那是一把伞吗？是的，它是。"),
    ("23", "Are these eggs? Yes, they are.", "这些是鸡蛋吗？是的，它们是。"),
    ("24", "Are those umbrellas? Yes, they are.", "那些是伞吗？是的，它们是。"),
    ("25", "Is that an orange? No, it isn't. It's an apple.", "那是一个橙子吗？不，它不是。它是一个苹果。"),
    ("26", "Is that a pencil? No, it isn't. It's a ruler.", "那是一支铅笔吗？不，它不是。它是一把尺子。"),
    ("27", "Is that a bag? No, it isn't. It's a book.", "那是一个书包吗？不，它不是。它是一本书。"),
    ("28", "Is that a desk? No, it isn't. It's a bed.", "那是一张课桌吗？不，它不是。它是一张床。"),
    ("29", "Is this a pencil? No, it isn't. It's a book.", "这是一支铅笔吗？不，它不是。它是一本书。"),
    ("30", "Is that an umbrella? No, it isn't. It's a bag.", "那是一把伞吗？不，它不是。它是一个书包。"),
]


def read_api_key() -> str:
    api_key = os.environ.get("MINIMAX_API_KEY", "").strip()
    if api_key:
        return api_key
    key_note = os.environ.get("MINIMAX_KEY_NOTE", "").strip()
    if not key_note:
        raise RuntimeError("Set MINIMAX_API_KEY or MINIMAX_KEY_NOTE before generating audio")
    text = pathlib.Path(key_note).read_text(encoding="utf-8", errors="ignore")

    def usable_key(candidate: str) -> str | None:
        candidate = candidate.strip().strip("`'\".,;:")
        if len(candidate) >= 80 and "REDACTED" not in candidate:
            return candidate
        return None

    # Codex session files are JSONL. Parsing strings avoids picking up escaped,
    # truncated, or masked fragments from raw log text.
    if key_note.endswith(".jsonl"):
        key_pattern = re.compile(r"sk-api-[A-Za-z0-9_.\-]+")

        def walk(value):
            if isinstance(value, str):
                yield value
            elif isinstance(value, dict):
                for child in value.values():
                    yield from walk(child)
            elif isinstance(value, list):
                for child in value:
                    yield from walk(child)

        for line in text.splitlines():
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue
            for value in walk(record):
                for match in key_pattern.finditer(value):
                    key = usable_key(match.group(0))
                    if key:
                        return key

    ordinary = re.search(r"(?im)^\s*minimax\s+api\s+key[^\n]*\n\s*(sk-api-[^\s]+)", text)
    if ordinary:
        key = usable_key(ordinary.group(1))
        if key:
            return key
    for any_key in re.finditer(r"sk-api-[A-Za-z0-9_.\-]+", text):
        key = usable_key(any_key.group(0))
        if key:
            return key
    raise RuntimeError("MiniMax API key not found in key note")


def synthesize(api_key: str, text: str, language_boost: str, voice_id: str, api_url: str) -> tuple[bytes, dict]:
    payload = {
        "model": MODEL,
        "text": text,
        "stream": False,
        "language_boost": language_boost,
        "output_format": "hex",
        "voice_setting": {
            "voice_id": voice_id,
            "speed": 1.08 if language_boost == "English" else 1.06,
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


def generate_one(
    api_key: str,
    text: str,
    language_boost: str,
    voice_id: str,
    output_path: pathlib.Path,
    asset_path: pathlib.Path,
) -> dict:
    if output_path.exists() and output_path.stat().st_size > 1024:
        asset_path.parent.mkdir(parents=True, exist_ok=True)
        asset_path.write_bytes(output_path.read_bytes())
        return {
            "text": text,
            "language_boost": language_boost,
            "voice_id": voice_id,
            "file": output_path.name,
            "bytes": output_path.stat().st_size,
            "reused": True,
        }
    last_error: Exception | None = None
    for api_url in API_URLS:
        try:
            audio, response = synthesize(api_key, text, language_boost, voice_id, api_url)
            output_path.parent.mkdir(parents=True, exist_ok=True)
            asset_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_bytes(audio)
            asset_path.write_bytes(audio)
            extra = response.get("extra_info") or {}
            return {
                "text": text,
                "language_boost": language_boost,
                "voice_id": voice_id,
                "file": output_path.name,
                "bytes": len(audio),
                "audio_length": extra.get("audio_length"),
                "usage_characters": extra.get("usage_characters"),
                "trace_id": response.get("trace_id"),
                "api_url": api_url,
            }
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f"MiniMax generation failed for {output_path.name}: {last_error}")


def lesson_json() -> dict:
    return {
        "id": "lesson2",
        "title": "Lesson 2",
        "subtitle": "Is this / Is that / Are these / Are those",
        "image": f"lessons/lesson2/{IMAGE_NAME}",
        "words": [
            {
                "english": word["english"],
                "chinese": word["chinese"],
                "english_audio": f"lessons/lesson2/words/audio/{word['id']}_en.mp3",
                "chinese_audio": f"lessons/lesson2/words/audio/{word['id']}_zh.mp3",
            }
            for word in WORDS
        ],
        "items": [
            {
                "text": english,
                "chinese": chinese,
                "audio": f"lessons/lesson2/audio/{item_id}.mp3",
                "chinese_audio": f"lessons/lesson2/audio/{item_id}_zh.mp3",
                "region": region,
            }
            for item_id, english, chinese, region in ITEMS
        ],
        "transfer_practice": [
            {
                "text": english,
                "chinese": chinese,
                "audio": f"lessons/lesson2/transfer/audio/{item_id}_en.mp3",
                "chinese_audio": f"lessons/lesson2/transfer/audio/{item_id}_zh.mp3",
            }
            for item_id, english, chinese in TRANSFER_ITEMS
        ],
    }


def write_lesson_assets() -> None:
    LESSON_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(IMAGE_SOURCE, LESSON_DIR / IMAGE_NAME)
    (LESSON_DIR / "lesson.json").write_text(
        json.dumps(lesson_json(), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    write_lesson_assets()
    if "--manifest-only" in sys.argv:
        print(f"wrote lesson manifest to {LESSON_DIR / 'lesson.json'}")
        return 0

    api_key = read_api_key()
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
        "words": [],
        "items": [],
        "transfer_items": [],
    }

    for word in WORDS:
        print(f"generating word {word['id']} English", flush=True)
        en = generate_one(
            api_key,
            word["english"],
            "English",
            ENGLISH_VOICE_ID,
            OUT_DIR / "words" / f"{word['id']}_en.mp3",
            LESSON_DIR / "words" / "audio" / f"{word['id']}_en.mp3",
        )
        time.sleep(0.2)
        print(f"generating word {word['id']} Chinese", flush=True)
        zh = generate_one(
            api_key,
            word["chinese"],
            "Chinese",
            CHINESE_VOICE_ID,
            OUT_DIR / "words" / f"{word['id']}_zh.mp3",
            LESSON_DIR / "words" / "audio" / f"{word['id']}_zh.mp3",
        )
        manifest["words"].append({**word, "english_audio": en, "chinese_audio": zh})
        time.sleep(0.2)

    for item_id, english, chinese, _region in ITEMS:
        print(f"generating sentence {item_id} English: {english}", flush=True)
        en = generate_one(
            api_key,
            english,
            "English",
            ENGLISH_VOICE_ID,
            OUT_DIR / "sentences" / f"{item_id}.mp3",
            LESSON_DIR / "audio" / f"{item_id}.mp3",
        )
        time.sleep(0.2)
        print(f"generating sentence {item_id} Chinese: {chinese}", flush=True)
        zh = generate_one(
            api_key,
            chinese,
            "Chinese",
            CHINESE_VOICE_ID,
            OUT_DIR / "sentences" / f"{item_id}_zh.mp3",
            LESSON_DIR / "audio" / f"{item_id}_zh.mp3",
        )
        manifest["items"].append({"id": item_id, "english": english, "chinese": chinese, "english_audio": en, "chinese_audio": zh})
        time.sleep(0.2)

    for item_id, english, chinese in TRANSFER_ITEMS:
        print(f"generating transfer {item_id} English: {english}", flush=True)
        en = generate_one(
            api_key,
            english,
            "English",
            ENGLISH_VOICE_ID,
            OUT_DIR / "transfer" / f"{item_id}_en.mp3",
            LESSON_DIR / "transfer" / "audio" / f"{item_id}_en.mp3",
        )
        time.sleep(0.2)
        print(f"generating transfer {item_id} Chinese: {chinese}", flush=True)
        zh = generate_one(
            api_key,
            chinese,
            "Chinese",
            CHINESE_VOICE_ID,
            OUT_DIR / "transfer" / f"{item_id}_zh.mp3",
            LESSON_DIR / "transfer" / "audio" / f"{item_id}_zh.mp3",
        )
        manifest["transfer_items"].append({"id": item_id, "english": english, "chinese": chinese, "english_audio": en, "chinese_audio": zh})
        time.sleep(0.2)

    manifest_text = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "manifest.json").write_text(manifest_text, encoding="utf-8")
    (LESSON_DIR / "audio" / "manifest.json").write_text(manifest_text, encoding="utf-8")
    print(f"wrote lesson2 assets to {LESSON_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
