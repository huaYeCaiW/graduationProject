#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Modbus TCP 从站模拟器，配合 donghuan 平台联调用。
依赖：pip install pymodbus==3.6.9
运行：python modbus_simulator.py --port 5020

寄存器布局（0 基，与 seed.sql 中 mb-01 的测点对应）：
  HR 0-1  float32  空气温度（AB CD）
  HR 2-3  float32  空气湿度（AB CD）
  HR 4    uint16   土壤湿度，实际值 ×10（scale=0.1）
  HR 5    uint16   光照，单位 lux
  DI 0    bool     卷帘状态：0 关 / 1 开
"""

import argparse
import random
import struct
import threading
import time

from pymodbus.datastore import (
    ModbusSequentialDataBlock,
    ModbusServerContext,
    ModbusSlaveContext,
)
from pymodbus.server import StartTcpServer

HR_TEMP = 0
HR_HUMI = 2
HR_SOIL = 4
HR_LIGHT = 5
DI_DOOR = 0


def to_words(value: float):
    """float32 按大端拆成两个 16 位寄存器（AB CD）"""
    hi, lo = struct.unpack(">HH", struct.pack(">f", value))
    return [hi, lo]


def updater(context):
    """每 2 秒更新一次寄存器，模拟现场数据缓慢变化"""
    temp, humi, soil, light = 25.0, 60.0, 45.0, 12000

    while True:
        temp = min(max(temp + random.uniform(-0.4, 0.4), 15.0), 40.0)
        humi = min(max(humi + random.uniform(-1.0, 1.0), 30.0), 85.0)
        soil = min(max(soil + random.uniform(-0.5, 0.5), 10.0), 80.0)
        light = max(0, light + random.randint(-500, 500))

        slave = context[0]
        slave.setValues(3, HR_TEMP, to_words(temp))
        slave.setValues(3, HR_HUMI, to_words(humi))
        slave.setValues(3, HR_SOIL, [int(round(soil * 10))])
        slave.setValues(3, HR_LIGHT, [light])
        slave.setValues(2, DI_DOOR, [1 if light > 10000 else 0])

        print(f"[模拟器] 温度={temp:.2f}℃ 湿度={humi:.2f}% "
              f"土壤={soil:.2f}% 光照={light}lx", flush=True)
        time.sleep(2)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=5020)
    args = parser.parse_args()

    store = ModbusSlaveContext(
        di=ModbusSequentialDataBlock(0, [0] * 100),
        co=ModbusSequentialDataBlock(0, [0] * 100),
        hr=ModbusSequentialDataBlock(0, [0] * 100),
        ir=ModbusSequentialDataBlock(0, [0] * 100),
        zero_mode=True,
    )
    context = ModbusServerContext(slaves=store, single=True)

    threading.Thread(target=updater, args=(context,), daemon=True).start()

    print(f"[模拟器] Modbus TCP 从站启动：{args.host}:{args.port}，unitId=1", flush=True)
    StartTcpServer(context=context, address=(args.host, args.port))


if __name__ == "__main__":
    main()
