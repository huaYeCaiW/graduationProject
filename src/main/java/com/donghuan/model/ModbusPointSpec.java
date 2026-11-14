package com.donghuan.model;

// 测点地址串：寄存器类型:地址:数据类型[:缩放]
public record ModbusPointSpec(RegisterType registerType, int address,
                              ModbusDataType dataType, double scale) {

    public static ModbusPointSpec parse(String addressText, String dataTypeCode) {
        if (addressText == null || addressText.isBlank()) {
            throw new IllegalArgumentException("address 为空");
        }
        String[] parts = addressText.trim().split(":", -1);
        if (parts.length < 2) {
            throw new IllegalArgumentException("address 应为 寄存器类型:地址:数据类型[:缩放]");
        }

        RegisterType registerType = RegisterType.from(parts[0]);
        int address = parseInt(parts[1], "寄存器地址");

        String typeCode = parts.length > 2 ? parts[2].trim() : "";
        if (typeCode.isEmpty()) {
            typeCode = dataTypeCode;
        }
        ModbusDataType dataType = ModbusDataType.from(typeCode);

        // 位寄存器必须配 bool，字寄存器不能配 bool
        if (registerType.isBit() != dataType.isBit()) {
            throw new IllegalArgumentException("寄存器类型与数据类型不匹配：" + parts[0] + " / " + typeCode);
        }

        String scaleText = parts.length > 3 ? parts[3].trim() : "";
        double scale = scaleText.isEmpty() ? 1D : parseDouble(scaleText, "缩放系数");

        return new ModbusPointSpec(registerType, address, dataType, scale);
    }

    // 位类型读 1 位，字类型按数据类型的寄存器数
    public int quantity() {
        return registerType.isBit() ? 1 : dataType.registerCount();
    }

    private static int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 不是整数: " + raw);
        }
    }

    private static double parseDouble(String raw, String field) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 不是数字: " + raw);
        }
    }
}
