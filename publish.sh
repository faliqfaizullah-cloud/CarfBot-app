#!/data/data/com.termux/files/usr/bin/bash
V="$1"
if [ -z "$V" ]; then echo "Usage: bash publish.sh v1.0.1"; exit 1; fi
git add .
git commit -m "Release $V"
git push origin main || { echo "PUSH FAILED"; exit 1; }
git tag "$V" && git push origin "$V" || { echo "TAG FAILED (version already used?)"; exit 1; }
echo "Done. Watch the build with: gh run watch"
