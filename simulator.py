# simulator.py - 假温湿度传感器，往 EMQX broker 发 MQTT 消息
import paho.mqtt.client as mqtt
import json
import time
import random

BROKER = "localhost"
PORT = 1883
TOPIC = "donghuan/th/01"
DEVICE_ID = "th-01"

client = mqtt.Client(client_id="simulator_" + DEVICE_ID, protocol=mqtt.MQTTv311)
client.connect(BROKER, PORT, keepalive=60)
print(f"✅ 已连 {BROKER}:{PORT} → {TOPIC}")
num = 0
client.loop_start()

try:
    while True:
        payload = {
            "temperature": round(20 + random.random() * 12, 1),   # 20~32℃
            "humidity":    round(300 + random.random() * 35, 1),   # 40~75%
            "ts":          int(time.time() * 1000)                 # 毫秒时间戳
        }
        client.publish(TOPIC, json.dumps(payload), qos=0)
        print(f"📤 {payload}")
        num += 1
        time.sleep(1)
except KeyboardInterrupt:
    print("\n🛑 停了")
    print(f"共{num}条")
    client.loop_stop()
    client.disconnect()