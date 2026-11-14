package com.donghuan.model;


import java.util.Locale;

// 数据类型：决定一次读几个寄存器，以及字节如何解释称数值。
public enum ModbusDataType {
    UINT16(1),
    INT16(1),
    UINT32(2),
    INT32(2),
    FLOAT32(2),
    BOOL(0);

    private final int registerCount;

    ModbusDataType(int registerCount) {
        this.registerCount = registerCount;
    }

    // 位类型不占寄存器，单独占 1 位
    public int registerCount() {
        return registerCount;
    }

    public boolean isBit() {
        return this == BOOL;
    }

    public double decode(int[] words) {
        if (words == null || words.length == 0) {
            throw new IllegalArgumentException("解码数据为空: " + this);
        }
        return switch (this) {
            case BOOL -> words[0] == 0 ? 0D : 1D;
            case UINT16 -> words[0] & 0xFFFF;
            case INT16 -> (short) words[0];
            case UINT32 -> ((long) (words[0] & 0xFFFF) << 16) | (words[1] & 0xFFFF);
            case INT32 -> ((words[0] & 0xFFFF) << 16) | (words[1] & 0xFFFF);
            case FLOAT32 -> Float.intBitsToFloat(
                    ((words[0] & 0xFFFF) << 16) | (words[1] & 0xFFFF));
        };
    }

    public static ModbusDataType from(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("数据类型为空");
        }
        return switch (code.trim().toLowerCase(Locale.ROOT)) {
            case "uint16", "u16", "word" -> UINT16;
            case "int16", "i16", "short" -> INT16;
            case "uint32", "u32" -> UINT32;
            case "int32", "i32" -> INT32;
            case "float32", "float", "f32" -> FLOAT32;
            case "bool", "bit", "boolean" -> BOOL;
            default -> throw new IllegalArgumentException("未知数据类型: " + code);
        };
    }
}
