#!/usr/bin/env bash
set -euo pipefail
# Preparation only. Activation is a separate explicitly authorized operator command.
cd /home/phive/Documents/CoinMind-AI
exec python3 scripts/release/prepare.py "$@"
