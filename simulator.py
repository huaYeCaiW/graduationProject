# simulator.py - 假温湿度传感器，往 EMQX broker 发 MQTT 消息
# 用法：python simulator.py [device_code]，默认 th-01
#   python simulator.py th-01
#   python simulator.py th-02
import paho.mqtt.client as mqtt
import json
import sys
import time
import random

BROKER = "localhost"
PORT = 1883
DEVICE_CODE = sys.argv[1] if len(sys.argv) > 1 else "th-01"
TOPIC = "donghuan/th/" + DEVICE_CODE.split("-")[-1]

client = mqtt.Client(client_id="simulator_" + DEVICE_CODE, protocol=mqtt.MQTTv311)
client.connect(BROKER, PORT, keepalive=60)
print(f"✅ 已连 {BROKER}:{PORT} → {TOPIC}（{DEVICE_CODE}）")
num = 0
client.loop_start()

try:
    while True:
        payload = {
            "temperature": round(20 + random.random() * 12, 1),   # 20~32℃
            "humidity":    round(30 + random.random() * 35, 1),   # 40~75%
            "ts":          int(time.time() * 1000)                 # 毫秒时间戳
        }
        client.publish(TOPIC, json.dumps(payload), qos=0)
        print(f"📤 [{DEVICE_CODE}] {payload}")
        num += 1
        time.sleep(0.5)
except KeyboardInterrupt:
    print("\n🛑 停了")
    print(f"共{num}条")
    client.loop_stop()
    client.disconnect()