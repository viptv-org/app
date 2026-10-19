#!/usr/bin/env python3
"""Rolling queue for DeepSeek fix agents.

The Ollama Cloud account pool only allows a couple of concurrent agents, so
spawning 25 at once fails with account_pool_exhausted. This keeps at most
LIMIT agents running, re-queues errored ones with backoff, and stops when every
FIX-* agent has run.

Usage: python3 tools/queue_deepseek.py [limit]
"""
import json
import subprocess
import sys
import time

LIMIT = int(sys.argv[1]) if len(sys.argv) > 1 else 2
WAIT = 20
MAX_ATTEMPTS = 6

CONTINUE = ("Continue your original FIX task. If you already produced the patch brief, "
            "just confirm the /tmp/fix-*.md path. Read-only: do not edit repository files.")


def agents():
    out = subprocess.run(["paseo", "ls", "--json"], capture_output=True, text=True, timeout=60).stdout
    try:
        data = json.loads(out)
    except json.JSONDecodeError:
        return []
    rows = data if isinstance(data, list) else data.get("agents", data)
    return [a for a in rows if str(a.get("name", "")).startswith("FIX-")]


def main():
    attempts = {}
    while True:
        rows = agents()
        if not rows:
            time.sleep(WAIT)
            continue
        running = [a for a in rows if a.get("status") == "running"]
        errored = [a for a in rows if a.get("status") == "error"]
        idle = [a for a in rows if a.get("status") == "idle"]
        pending = [a for a in errored + idle if attempts.get(a["id"], 0) < MAX_ATTEMPTS]
        print(f"[queue] running={len(running)} errored={len(errored)} idle={len(idle)} pending={len(pending)}", flush=True)
        if not running and not pending:
            print("[queue] done", flush=True)
            return
        slots = LIMIT - len(running)
        for agent in pending[:max(0, slots)]:
            attempts[agent["id"]] = attempts.get(agent["id"], 0) + 1
            print(f"[queue] requeue {agent['name']} attempt {attempts[agent['id']]}", flush=True)
            subprocess.run(["paseo", "send", "--no-wait", agent["id"], CONTINUE],
                           capture_output=True, text=True, timeout=60)
            time.sleep(4)
        time.sleep(WAIT)


if __name__ == "__main__":
    main()
