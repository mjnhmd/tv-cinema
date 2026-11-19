#!/usr/bin/env python3
"""TV Cinema event sink: a tiny append-only HTTP endpoint for the diagnostic log.

The television batches its events (see CinemaEventUpload) and POSTs the pending
lines here; this process only appends them to a dated file, so the cost per
request is a single write and the data survives a restart of either side.

Routes
    POST /events        text/plain body, one event per line -> append, 200
    POST /restart       exit so the container runtime reloads this file, 200
    GET  /health        uptime, line counts, known sessions
    GET  /tail?n=200    the newest n lines of today (or ?date=YYYYMMDD)
    GET  /files         per-day files with sizes
    GET  /file?date=..  one day's whole file

Auth: Authorization: Bearer $CINEMA_SINK_TOKEN on every route.

Environment
    CINEMA_SINK_TOKEN  shared secret, required (refuse to start without it)
    CINEMA_SINK_DIR    output directory, default ./events
    CINEMA_SINK_PORT   listen port, default 13000
    CINEMA_SINK_HOST   listen address, default 0.0.0.0
    CINEMA_SINK_KEEP_DAYS   delete day files older than this, default 90 (0 keeps all)
    CINEMA_SINK_MAX_BYTES   per-day size cap, default 32 MiB (0 = no cap)

Growth is bounded on purpose: each day writes its own file, files older than
CINEMA_SINK_KEEP_DAYS are pruned at the first write of a new day, and a day file
stops growing at CINEMA_SINK_MAX_BYTES (one notice line records the cut).

Run: python3 server.py   (no third-party dependency, Python 3.8+)
"""

import datetime
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

TOKEN = os.environ.get("CINEMA_SINK_TOKEN", "").strip()
DIR = os.path.abspath(os.environ.get("CINEMA_SINK_DIR", "./events"))
PORT = int(os.environ.get("CINEMA_SINK_PORT", "13000"))
HOST = os.environ.get("CINEMA_SINK_HOST", "0.0.0.0")
MAX_BODY = 1 << 20
MAX_TAIL = 5000
KEEP_DAYS = int(os.environ.get("CINEMA_SINK_KEEP_DAYS", "90"))
MAX_BYTES = int(os.environ.get("CINEMA_SINK_MAX_BYTES", str(32 << 20)))

_lock = threading.Lock()
_started = time.time()
_sessions = set()
_pruned = None
_capped = set()


def today():
    return datetime.datetime.now().strftime("%Y%m%d")


def path_for(date):
    return os.path.join(DIR, "%s.log" % date)


def count_lines(date):
    path = path_for(date)
    if not os.path.exists(path):
        return 0
    with open(path, "rb") as handle:
        return sum(1 for _ in handle)


def prune(current):
    """Drop day files older than KEEP_DAYS; run once per day, not per request."""
    global _pruned
    if _pruned == current or KEEP_DAYS <= 0:
        return
    _pruned = current
    limit = datetime.datetime.now() - datetime.timedelta(days=KEEP_DAYS)
    cutoff = limit.strftime("%Y%m%d")
    try:
        names = os.listdir(DIR)
    except OSError:
        return
    for name in names:
        if not name.endswith(".log") or len(name) != 12:
            continue
        if name[:-4] >= cutoff:
            continue
        try:
            os.remove(os.path.join(DIR, name))
            sys.stderr.write("pruned %s\n" % name)
        except OSError:
            pass


def append(date, lines, session, address):
    """Append one batch; returns the number of lines actually stored."""
    stamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
    payload = "".join("%s %s [%s] %s\n" % (stamp, session or "-", address, line) for line in lines)
    size = len(payload.encode("utf-8", "replace"))
    with _lock:
        os.makedirs(DIR, exist_ok=True)
        prune(date)
        path = path_for(date)
        if date in _capped:
            return 0
        current = os.path.getsize(path) if os.path.exists(path) else 0
        if MAX_BYTES > 0 and current + size > MAX_BYTES:
            _capped.add(date)
            note = "%s %s [sink] day file hit its %d byte cap, dropping the rest of today\n" % (
                stamp, session or "-", MAX_BYTES)
            with open(path, "ab") as handle:
                handle.write(note.encode("utf-8", "replace"))
            sys.stderr.write("capped %s at %d bytes\n" % (date, MAX_BYTES))
            return 0
        with open(path, "ab") as handle:
            handle.write(payload.encode("utf-8", "replace"))
        return len(lines)


def tail(date, count):
    path = path_for(date)
    if not os.path.exists(path):
        return ""
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        rows = handle.readlines()
    return "".join(rows[-count:])


class Handler(BaseHTTPRequestHandler):
    server_version = "CinemaSink/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))

    def send(self, code, body, content_type="application/json; charset=utf-8"):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        header = self.headers.get("Authorization", "")
        if header.startswith("Bearer ") and header[7:].strip() == TOKEN:
            return True
        self.send(401, '{"ok":false,"error":"unauthorized"}')
        return False

    def do_POST(self):
        route = urlparse(self.path).path
        if route == "/restart":
            if not self.authorized():
                return
            self.send(200, '{"ok":true,"restarting":true}')
            # Exit after the response is flushed; restart:always brings us back
            # with whatever server.py now holds.
            threading.Timer(0.3, lambda: os._exit(0)).start()
            return
        if route != "/events":
            self.send(404, '{"ok":false,"error":"not found"}')
            return
        if not self.authorized():
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            self.send(400, '{"ok":false,"error":"empty body"}')
            return
        if length > MAX_BODY:
            self.send(413, '{"ok":false,"error":"body too large"}')
            return
        body = self.rfile.read(length).decode("utf-8", "replace")
        lines = [line for line in body.splitlines() if line.strip()]
        if not lines:
            self.send(400, '{"ok":false,"error":"no lines"}')
            return
        session = (self.headers.get("X-Cinema-Session") or "-").strip()
        address = self.headers.get("X-Cinema-Version") or self.client_address[0]
        _sessions.add(session)
        stored = append(today(), lines, session, address)
        self.send(200, '{"ok":true,"lines":%d,"stored":%d}' % (len(lines), stored))

    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        if parsed.path == "/health":
            if not self.authorized():
                return
            date = today()
            self.send(200, '{"ok":true,"uptime_s":%d,"date":"%s","lines_today":%d,"sessions":%d'
                      ',"keep_days":%d,"max_bytes":%d}'
                      % (int(time.time() - _started), date, count_lines(date), len(_sessions),
                         KEEP_DAYS, MAX_BYTES))
            return
        if not self.authorized():
            return
        if parsed.path == "/tail":
            return self.handle_tail(params)
        if parsed.path == "/files":
            return self.handle_files()
        if parsed.path == "/file":
            return self.handle_file(params)
        self.send(404, '{"ok":false,"error":"not found"}')

    def handle_tail(self, params):
        date = (params.get("date") or [today()])[0]
        try:
            count = min(int((params.get("n") or ["200"])[0]), MAX_TAIL)
        except ValueError:
            count = 200
        self.send(200, tail(date, count) or "(empty)", "text/plain; charset=utf-8")

    def handle_files(self):
        rows = []
        if os.path.isdir(DIR):
            for name in sorted(os.listdir(DIR)):
                if name.endswith(".log"):
                    info = os.stat(os.path.join(DIR, name))
                    rows.append('{"date":"%s","bytes":%d}' % (name[:-4], info.st_size))
        self.send(200, '{"ok":true,"dir":"%s","files":[%s]}' % (DIR, ",".join(rows)))

    def handle_file(self, params):
        date = (params.get("date") or [today()])[0]
        if not date.isdigit() or len(date) != 8:
            self.send(400, '{"ok":false,"error":"date must be YYYYMMDD"}')
            return
        if not os.path.exists(path_for(date)):
            self.send(404, '{"ok":false,"error":"no such day"}')
            return
        with open(path_for(date), "r", encoding="utf-8", errors="replace") as handle:
            self.send(200, handle.read(), "text/plain; charset=utf-8")


def main():
    if not TOKEN:
        sys.stderr.write("CINEMA_SINK_TOKEN is required\n")
        return 2
    os.makedirs(DIR, exist_ok=True)
    prune(today())
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    sys.stderr.write("cinema sink on http://%s:%d -> %s\n" % (HOST, PORT, DIR))
    server.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
