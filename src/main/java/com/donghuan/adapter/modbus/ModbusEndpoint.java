package com.donghuan.adapter.modbus;

// 连接串 host:port[:unitId]
public record ModbusEndpoint(String host, int port, int unitId) {

    public static ModbusEndpoint parse(String config) {
        if (config == null || config.isBlank()) {
            throw new IllegalArgumentException("连接配置为空");
        }
        String[] parts = config.trim().split(":", -1);
        if (parts.length < 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("连接配置应为 host:port[:unitId]: " + config);
        }
        try {
            String host = parts[0].trim();
            int port = Integer.parseInt(parts[1].trim());
            int unitId = parts.length > 2 && !parts[2].isBlank()
                    ? Integer.parseInt(parts[2].trim())
                    : 1;
            return new ModbusEndpoint(host, port, unitId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("端口或从站地址不是整数: " + config);
        }
    }
}