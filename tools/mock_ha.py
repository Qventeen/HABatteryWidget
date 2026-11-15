#!/usr/bin/env python3
"""Mock Home Assistant: serves POST /api/template for manual checks.

The Authorization header is ignored and never printed.
"""
import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


def parse_temps(text):
    if not text:
        return []
    return [None if p.strip() in ("", "null") else float(p) for p in text.split(",")]


def make_handler(args):
    body = json.dumps(
        {
            "soc": args.soc,
            "total_v": args.total_v,
            "current": args.current,
            "power": args.power,
            "ah_left": args.ah_left,
            "avg_cell": args.avg_cell,
            "delta": args.delta,
            "mosfet": args.mosfet,
            "temps": parse_temps(args.temps),
            "balancing": args.balancing,
        }
    ).encode()

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length") or 0)
            self.rfile.read(length)
            if self.path != "/api/template":
                self.send_error(404)
            elif args.status != 200:
                self.send_error(args.status)
            else:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        def do_GET(self):
            self.send_error(404)

        def log_message(self, fmt, *a):
            # Request line and status only; headers (token) are never logged.
            print("%s %s" % (self.address_string(), fmt % a), flush=True)

    return Handler


def main():
    p = argparse.ArgumentParser(description=__doc__)
    # Loopback only by default: it accepts any token. The emulator reaches it at 10.0.2.2.
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=8123)
    p.add_argument("--soc", type=float)
    p.add_argument("--total-v", dest="total_v", type=float)
    p.add_argument("--current", type=float)
    p.add_argument("--power", type=float)
    p.add_argument("--ah-left", dest="ah_left", type=float)
    p.add_argument("--avg-cell", dest="avg_cell", type=float)
    p.add_argument("--delta", type=float)
    p.add_argument("--mosfet", type=float)
    p.add_argument("--temps", help='comma-separated degrees C, e.g. "25,23"')
    p.add_argument("--balancing", choices=["on", "off"], help="raw balancing state")
    p.add_argument("--status", type=int, default=200, help="HTTP status to return")
    args = p.parse_args()
    HTTPServer((args.host, args.port), make_handler(args)).serve_forever()


if __name__ == "__main__":
    main()
