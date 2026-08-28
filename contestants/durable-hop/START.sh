#!/usr/bin/env bash
# durable-hop contestant: serve todo.hop with hopd (the durable package's
# runtime, a sibling checkout at ~/programming/durable).
set -e

PORT="${1:-4006}"
WS_PORT="${2:-4106}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DURABLE="$(cd "$HERE/../../../durable" && pwd)"

# hopd embeds web/index.html + glue.js at compile time — always rebuild
# (incremental; a no-op when fresh) so the contestant tracks the package.
(cd "$DURABLE" && cargo build -p hoprt --bin hopd)

if [ ! -f "$DURABLE/hop-web/pkg/hop_web.js" ]; then
    (cd "$DURABLE" && wasm-pack build hop-web --target web)
fi

exec "$DURABLE/target/debug/hopd" "$HERE/todo.hop" "$PORT" "$WS_PORT" \
    --data "$HERE/data" \
    --web "$DURABLE/hop-web/pkg"
