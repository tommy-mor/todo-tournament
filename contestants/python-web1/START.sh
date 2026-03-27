#!/usr/bin/env bash
set -e
PORT=${1:-4001}
cd "$(dirname "$0")"
mise install
exec mise exec -- uv run uvicorn app:app --port "$PORT" --host 0.0.0.0
