#!/usr/bin/env bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Ports for each contestant
declare -A PORTS=(
    ["python-web1"]=4001
    ["clojure-evaleval"]=4002
    ["python-evaleval"]=4003
    ["racket-evaleval"]=4004
    ["clojure-buzz"]=4005
)

# Start all contestants in background
echo -e "${YELLOW}Starting all contestants...${NC}"
declare -A PIDS

for contestant in python-web1 clojure-evaleval python-evaleval racket-evaleval clojure-buzz; do
    port=${PORTS[$contestant]}
    contestant_dir="$REPO_ROOT/contestants/$contestant"

    echo -e "  Starting $contestant on port $port..."

    if [ "$contestant" = "racket-evaleval" ]; then
        # Racket doesn't have START.sh, run directly
        (cd "$contestant_dir" && racket main.rkt $port) &
        PIDS[$contestant]=$!
    else
        # Use START.sh for other contestants
        bash "$contestant_dir/START.sh" $port &
        PIDS[$contestant]=$!
    fi
done

# Wait for all services to be ready
echo -e "${YELLOW}Waiting for services to start...${NC}"
for contestant in python-web1 clojure-evaleval python-evaleval racket-evaleval clojure-buzz; do
    port=${PORTS[$contestant]}
    max_attempts=30
    attempt=0

    while ! curl -s "http://localhost:$port" > /dev/null 2>&1; do
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${RED}✗ $contestant (port $port) failed to start${NC}"
            # Kill all background processes
            for pid in "${PIDS[@]}"; do
                kill $pid 2>/dev/null || true
            done
            exit 1
        fi
        attempt=$((attempt + 1))
        sleep 0.5
    done
    echo -e "  ${GREEN}✓${NC} $contestant ready on port $port"
done

echo

# Run test suite against each contestant
echo -e "${YELLOW}Running test suite against all contestants...${NC}"
echo

total_failures=0

for contestant in python-web1 clojure-evaleval python-evaleval racket-evaleval clojure-buzz; do
    port=${PORTS[$contestant]}
    url="http://localhost:$port"

    echo -e "${YELLOW}Testing $contestant on $url${NC}"

    if clj -M:validate "$url"; then
        echo -e "${GREEN}✓ $contestant passed all tests${NC}"
    else
        failures=$?
        echo -e "${RED}✗ $contestant had failures${NC}"
        total_failures=$((total_failures + failures))
    fi
    echo
done

# Cleanup
echo -e "${YELLOW}Shutting down contestants...${NC}"
for contestant in "${!PIDS[@]}"; do
    pid=${PIDS[$contestant]}
    if kill -0 $pid 2>/dev/null; then
        kill $pid 2>/dev/null || true
        echo -e "  Stopped $contestant (PID $pid)"
    fi
done

# Wait for processes to terminate
sleep 1

if [ $total_failures -gt 0 ]; then
    echo -e "${RED}Some tests failed. Total failures: $total_failures${NC}"
    exit $total_failures
else
    echo -e "${GREEN}All contestants passed!${NC}"
    exit 0
fi
