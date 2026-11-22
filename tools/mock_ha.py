#!/usr/bin/env python3
"""Mock Home Assistant for manual checks.

Serves POST /api/template (admin token), GET /api/states and
GET /api/states/<id> (user token). The Authorization header is ignored and
never printed.
"""
import argparse
import json
import re
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
            "capacity": args.capacity,
            "avg_cell": args.avg_cell,
            "delta": args.delta,
            "mosfet": args.mosfet,
            "temps": parse_temps(args.temps),
            "balancing": args.balancing,
        }
    ).encode()

    temps = parse_temps(args.temps)
    # The sensor-list template (spec 0004) names the prefix in its match pattern.
    sensor_pattern = re.compile(r"sensor\[\.\]([a-z0-9_]+)_temperature_sensor_\[0-9\]\+")

    def sensor_list(template):
        m = sensor_pattern.search(template)
        if not m or m.group(1) != args.prefix:
            return []
        return ["sensor.%s_temperature_sensor_%d" % (args.prefix, i + 1) for i in range(len(temps))]

    # The entity ids EntityFill makes from the prefix, each with its option value.
    p = args.prefix
    values = {
        "sensor.%s_state_of_charge" % p: args.soc,
        "sensor.%s_total_voltage" % p: args.total_v,
        "sensor.%s_current" % p: args.current,
        "sensor.%s_power" % p: args.power,
        "sensor.%s_capacity_remaining" % p: args.ah_left,
        "sensor.%s_total_battery_capacity" % p: args.capacity,
        "sensor.%s_average_cell_voltage" % p: args.avg_cell,
        "sensor.%s_delta_cell_voltage" % p: args.delta,
        "sensor.%s_power_tube_temperature" % p: args.mosfet,
        "sensor.%s_balancing" % p: args.balancing,
    }
    for i, t in enumerate(temps):
        values["sensor.%s_temperature_sensor_%d" % (p, i + 1)] = t

    def state_of(entity_id):
        v = values[entity_id]
        return "unknown" if v is None else str(v)

    class Handler(BaseHTTPRequestHandler):
        def send_json(self, status, payload):
            data = json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_POST(self):
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length)
            if self.path != "/api/template":
                self.send_error(404)
            elif args.status != 200:
                self.send_error(args.status)
            elif args.user_token:
                self.send_json(401, {"message": "Unauthorized"})
            else:
                template = json.loads(raw or b"{}").get("template", "")
                data = body
                if "select('match'" in template:
                    data = json.dumps(sensor_list(template)).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

        def do_GET(self):
            if args.status != 200:
                self.send_error(args.status)
            elif self.path == "/api/states":
                self.send_json(
                    200, [{"entity_id": e, "state": state_of(e)} for e in values]
                )
            elif self.path.startswith("/api/states/"):
                entity_id = self.path[len("/api/states/"):]
                if entity_id in values:
                    self.send_json(
                        200, {"entity_id": entity_id, "state": state_of(entity_id)}
                    )
                else:
                    self.send_json(404, {"message": "Entity not found."})
            else:
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
    p.add_argument("--capacity", type=float, help="total battery capacity, Ah")
    p.add_argument("--avg-cell", dest="avg_cell", type=float)
    p.add_argument("--delta", type=float)
    p.add_argument("--mosfet", type=float)
    p.add_argument("--temps", help='comma-separated degrees C, e.g. "25,23"')
    p.add_argument("--balancing", choices=["on", "off"], help="raw balancing state")
    p.add_argument("--prefix", default="bps_bms", help="BMS prefix that has --temps sensors")
    p.add_argument("--status", type=int, default=200, help="HTTP status to return")
    p.add_argument(
        "--user-token", dest="user_token", action="store_true",
        help="act as a non-admin token: POST /api/template returns 401",
    )
    args = p.parse_args()
    HTTPServer((args.host, args.port), make_handler(args)).serve_forever()


if __name__ == "__main__":
    main()
