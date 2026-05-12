# Test audio (not committed by default)

Run from the repository root:

```bash
chmod +x scripts/fetch-test-audio.sh   # once
./scripts/fetch-test-audio.sh
```

Downloaded bytes are listed in `.gitignore` in this directory. With **ffmpeg** on `PATH`, the script also writes **WAV**, **FLAC**, and **M4A** versions of the Wikimedia sample (same license as the `.ogg`). See `docs/agent-local-media-testing.md` for licenses and validation steps.
