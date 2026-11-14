package com.donghuan.model;


import java.util.Locale;

// Modbus 四类寄存器，分别对应功能码 1/2/3/4。
public enum RegisterType {
    COIL(1),
    DISCRETE_INPUT(2),
    HOLDING(3),
    INPUT(4);

    private final int functionCode;

    RegisterType(int functionCode) {
        this.functionCode = functionCode;
    }

    public int functionCode() {
        return functionCode;
    }

    // 位类型寄存器读 1 位，其余读 16 位数字
    public boolean isBit() {
        return this == COIL || this == DISCRETE_INPUT;
    }

    // 只读：输入寄存器与离散输入不允许写
    public static RegisterType from(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("寄存器类型为空");
        }
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "coil", "co" -> COIL;
            case "discrete", "discrete_input", "di" -> DISCRETE_INPUT;
            case "holding", "holding_register", "hr" -> HOLDING;
            case "input", "input_register", "ir" -> INPUT;
            default -> throw new IllegalArgumentException("未知寄存器类型: " + code);
        };
    }
}
