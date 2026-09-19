#!/usr/bin/env python3
from __future__ import annotations
import argparse
import pathlib
import subprocess
import sys

PACKAGE = "io.haru.assistant"

def adb(*args: str, capture: bool = False):
    kwargs = {"check": True}
    if capture:
        kwargs.update(stdout=subprocess.PIPE, text=True)
    return subprocess.run(["adb", *args], **kwargs)

def find_model() -> str:
    result = adb(
        "shell", "run-as", PACKAGE, "sh", "-c",
        "ls files/models/*.gguf 2>/dev/null | head -1",
        capture=True,
    )
    value = result.stdout.strip()
    if not value:
        raise SystemExit("No installed GGUF model was found in HARU.")
    return value

def backup(model_path: str, output: pathlib.Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        proc = subprocess.run(
            ["adb", "exec-out", "run-as", PACKAGE, "cat", model_path],
            stdout=handle,
        )
    if proc.returncode != 0 or output.stat().st_size < 1_000_000:
        output.unlink(missing_ok=True)
        raise SystemExit("Model backup failed.")
    print(f"Backed up {output.name}: {output.stat().st_size / 1024 / 1024:.0f} MB")

def restore(input_file: pathlib.Path) -> None:
    remote = f"/data/local/tmp/{input_file.name}"
    adb("push", str(input_file), remote)
    adb("shell", "chmod", "644", remote)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "files/models")
    adb("shell", "run-as", PACKAGE, "cp", remote, f"files/models/{input_file.name}")
    adb("shell", "rm", "-f", remote)
    print("Model restored. Open HARU > Local AI and tap Load model once.")

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, help="Path to the new HARU APK")
    parser.add_argument("--backup", default="HARU-model-backup.gguf")
    args = parser.parse_args()

    apk = pathlib.Path(args.apk).resolve()
    backup_file = pathlib.Path(args.backup).resolve()
    if not apk.exists():
        raise SystemExit(f"APK not found: {apk}")

    adb("wait-for-device")
    model_path = find_model()
    model_name = pathlib.PurePosixPath(model_path).name
    backup_file = backup_file.with_name(model_name)
    backup(model_path, backup_file)

    print("Replacing old HARU install...")
    adb("uninstall", PACKAGE)
    adb("install", str(apk))
    restore(backup_file)
    print("Migration complete. No local AI re-download was required.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
