# simulator.py - MQTT 设备模拟器
#
# 单设备：
#   python simulator.py th-01
#
# 多设备（一个进程）：
#   python simulator.py th-01 th-02
#
# 自动发现服务器上的全部 MQTT 设备与测点：
#   python simulator.py --all --api http://123.57.188.160:8080 --username admin --password password
#
# MQTT 连接参数优先取命令行，其次取环境变量：
#   MQTT_BROKER / MQTT_PORT / MQTT_USERNAME / MQTT_PASSWORD
#
# 其他：
#   --interval 1        每台设备发送间隔（秒）
#   --alarm-rate 0.03   每个测点越限的概率，用于触发告警
import argparse
import json
import os
import random
import sys
import time

import paho.mqtt.client as mqtt

try:
    import requests
except ImportError:
    requests = None


def parse_args():
    p = argparse.ArgumentParser(description="MQTT 设备模拟器")
    p.add_argument("devices", nargs="*", help="设备编码，如 th-01 th-02")
    p.add_argument("--all", action="store_true", help="通过 API 自动发现全部 MQTT 设备")
    p.add_argument("--api", default=os.getenv("API_BASE", ""), help="应用地址，如 http://IP:8080")
    p.add_argument("--username", default=os.getenv("API_USERNAME", "admin"))
    p.add_argument("--password", default=os.getenv("API_PASSWORD", "password"))
    p.add_argument("--broker", default=os.getenv("MQTT_BROKER", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.getenv("MQTT_PORT", "1883")))
    p.add_argument("--mqtt-username", default=os.getenv("MQTT_USERNAME", ""))
    p.add_argument("--mqtt-password", default=os.getenv("MQTT_PASSWORD", ""))
    p.add_argument("--interval", type=float, default=1.0)
    p.add_argument("--alarm-rate", type=float, default=0.03)
    return p.parse_args()


def fetch_devices(args):
    if requests is None:
        sys.exit("需要 requests：pip install requests")
    if not args.api:
        sys.exit("--all 需要同时提供 --api，例如 --api http://123.57.188.160:8080")

    session = requests.Session()
    resp = session.post(
        args.api.rstrip("/") + "/api/auth/login",
        json={"username": args.username, "password": args.password},
        timeout=10,
    )
    if resp.status_code != 200:
        sys.exit(f"登录失败：HTTP {resp.status_code} {resp.text[:200]}")

    resp = session.get(args.api.rstrip("/") + "/api/devices", timeout=10)
    if resp.status_code != 200:
        sys.exit(f"获取设备失败：HTTP {resp.status_code} {resp.text[:200]}")

    devices = []
    for d in resp.json():
        if d.get("protocol") != "MQTT":
            continue
        topic = (d.get("connection") or {}).get("config") or ""
        if not topic.strip():
            continue
        points = []
        for pt in d.get("points", []):
            path = (pt.get("jsonPath") or "").strip()
            if not path.startswith("$."):
                continue
            points.append({
                "path": path[2:],
                "min": pt.get("min"),
                "max": pt.get("max"),
            })
        if not points:
            continue
        devices.append({"code": d["deviceCode"], "topic": topic, "points": points})

    if not devices:
        sys.exit("没有发现可用的 MQTT 设备")
    return devices


def manual_devices(codes):
    devices = []
    for code in codes:
        devices.append({
            "code": code,
            "topic": "donghuan/th/" + code.split("-")[-1],
            "points": [
                {"path": "temperature", "min": 18, "max": 38},
                {"path": "humidity", "min": 30, "max": 80},
            ],
        })
    return devices


def set_path(obj, path, value):
    parts = path.split(".")
    cur = obj
    for part in parts[:-1]:
        cur = cur.setdefault(part, {})
    cur[parts[-1]] = value


def point_value(point, alarm_rate):
    lo = point.get("min")
    hi = point.get("max")
    if not isinstance(lo, (int, float)) or not isinstance(hi, (int, float)) or hi <= lo:
        lo, hi = 0.0, 100.0

    if random.random() < alarm_rate:
        # 越上限或越下限，用于触发告警
        value = hi + (hi - lo) * random.uniform(0.05, 0.2) if random.random() < 0.5 \
            else lo - (hi - lo) * random.uniform(0.05, 0.2)
    else:
        value = random.uniform(lo, hi)
    return round(value, 2)


def build_payload(device, alarm_rate):
    payload = {}
    for point in device["points"]:
        set_path(payload, point["path"], point_value(point, alarm_rate))
    set_path(payload, "ts", int(time.time() * 1000))
    return payload


def main():
    args = parse_args()

    if args.all:
        devices = fetch_devices(args)
    elif args.devices:
        devices = manual_devices(args.devices)
    else:
        sys.exit("请指定设备编码，或使用 --all --api http://IP:8080")

    client = mqtt.Client(client_id="donghuan_sim_all", protocol=mqtt.MQTTv311)
    if args.mqtt_username:
        client.username_pw_set(args.mqtt_username, args.mqtt_password)
    client.connect(args.broker, args.port, keepalive=60)
    client.loop_start()

    print(f"已连接 {args.broker}:{args.port}，设备数 {len(devices)}，间隔 {args.interval}s")
    for d in devices:
        print(f"  {d['code']} -> {d['topic']}（{len(d['points'])} 个测点）")

    count = 0
    try:
        while True:
            for d in devices:
                payload = build_payload(d, args.alarm_rate)
                client.publish(d["topic"], json.dumps(payload), qos=1)
                print(f"[{d['code']}] {payload}")
            count += 1
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print(f"\n已停止，共发送 {count} 轮")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
