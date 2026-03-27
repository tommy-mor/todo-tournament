#!/usr/bin/env bash
set -e
PORT=${1:-4002}
cd "$(dirname "$0")"
mise install
exec mise exec -- clj -M:serve "$PORT"
