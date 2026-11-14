package com.donghuan.adapter.modbus;

import com.donghuan.model.RegisterType;
import lombok.Getter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * 极简 Modbus TCP 主站，只负责一次读请求。
 * 报文 = MBAP 头（7 字节）+ PDU，无第三方依赖。
 * 非线程安全，调用方需保证同一实例串行使用（方法已加 synchronized）。
 */
public class ModbusTcpClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final int unitId;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int transactionId;

    public ModbusTcpClient(String host, int port, int unitId) {
        this(host, port, unitId, 3000, 3000);
    }

    public ModbusTcpClient(String host, int port, int unitId, int connectTimeoutMs, int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.unitId = unitId;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * 读寄存器。
     *
     * @param type     寄存器类型，决定功能码 1/2/3/4
     * @param address  起始地址，0 基
     * @param quantity 读取数量：位类型为位数，字类型为寄存器个数
     * @return 位类型返回 0/1 数组；字类型返回每个寄存器的 16 位无符号值
     */
    public synchronized int[] read(RegisterType type, int address, int quantity) throws IOException {
        if (address < 0 || address > 0xFFFF) {
            throw new IllegalArgumentException("寄存器地址越界: " + address);
        }
        int max = type.isBit() ? 2000 : 125;
        if (quantity < 1 || quantity > max) {
            throw new IllegalArgumentException("读取数量越界: " + quantity + "，上限 " + max);
        }

        ensureConnected();

        int fc = type.functionCode();
        int tid = nextTransactionId();

        ByteBuffer req = ByteBuffer.allocate(12);
        req.putShort((short) tid);      // 事务标识
        req.putShort((short) 0);        // 协议标识，Modbus 固定 0
        req.putShort((short) 6);        // 后续长度：unitId(1) + PDU(5)
        req.put((byte) unitId);         // 单元标识
        req.put((byte) fc);             // 功能码
        req.putShort((short) address);  // 起始地址
        req.putShort((short) quantity); // 数量

        out.write(req.array());
        out.flush();

        // ---- MBAP 头（7 字节） ----
        ByteBuffer header = ByteBuffer.wrap(readFully(7));
        int respTid = header.getShort() & 0xFFFF;
        int respProtocol = header.getShort() & 0xFFFF;
        int respLength = header.getShort() & 0xFFFF;
        int respUnitId = header.get() & 0xFF;

        if (respTid != tid) {
            throw new IOException("事务标识不匹配：期望 " + tid + "，实际 " + respTid);
        }
        if (respProtocol != 0) {
            throw new IOException("协议标识非 0: " + respProtocol);
        }
        if (respUnitId != unitId) {
            throw new IOException("单元标识不匹配：期望 " + unitId + "，实际 " + respUnitId);
        }

        int pduLength = respLength - 1;   // 长度字段包含 unitId，PDU 要减 1
        if (pduLength <= 0) {
            throw new IOException("MBAP 长度非法: " + respLength);
        }
        byte[] pdu = readFully(pduLength);

        int respFc = pdu[0] & 0xFF;
        if (respFc == (fc | 0x80)) {
            int code = pdu.length > 1 ? pdu[1] & 0xFF : -1;
            throw new ModbusException(code,
                    "Modbus 异常响应：fc=" + fc + "，异常码=" + code + "（" + exceptionText(code) + "）");
        }
        if (respFc != fc) {
            throw new IOException("功能码不匹配：期望 " + fc + "，实际 " + respFc);
        }
        if (pdu.length < 2) {
            throw new IOException("PDU 长度不足: " + pdu.length);
        }

        int byteCount = pdu[1] & 0xFF;
        if (pdu.length != 2 + byteCount) {
            throw new IOException("字节数不符：声明 " + byteCount + "，实际 " + (pdu.length - 2));
        }

        if (type.isBit()) {
            int expected = (quantity + 7) / 8;
            if (byteCount != expected) {
                throw new IOException("位数据字节数不符：期望 " + expected + "，实际 " + byteCount);
            }
            int[] bits = new int[quantity];
            for (int i = 0; i < quantity; i++) {
                int value = pdu[2 + i / 8] & 0xFF;
                bits[i] = (value >> (i % 8)) & 0x01;
            }
            return bits;
        }

        if (byteCount != quantity * 2) {
            throw new IOException("寄存器字节数不符：期望 " + quantity * 2 + "，实际 " + byteCount);
        }
        int[] words = new int[quantity];
        for (int i = 0; i < quantity; i++) {
            words[i] = ((pdu[2 + i * 2] & 0xFF) << 8) | (pdu[3 + i * 2] & 0xFF);
        }
        return words;
    }

    // 连接已存在则复用，断线由调用方 close 后下一轮重建
    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        closeQuietly();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        s.setSoTimeout(readTimeoutMs);
        s.setTcpNoDelay(true);
        socket = s;
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) {
                throw new EOFException("Modbus 连接被对端关闭");
            }
            offset += n;
        }
        return buf;
    }

    private int nextTransactionId() {
        transactionId = (transactionId + 1) & 0xFFFF;
        return transactionId;
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        in = null;
        out = null;
    }

    private static String exceptionText(int code) {
        return switch (code) {
            case 1 -> "非法功能码";
            case 2 -> "非法数据地址";
            case 3 -> "非法数据值";
            case 4 -> "从站设备故障";
            case 5 -> "确认，命令处理中";
            case 6 -> "从站设备忙";
            case 8 -> "存储奇偶校验错误";
            case 10 -> "网关路径不可用";
            case 11 -> "网关目标设备无响应";
            default -> "未知异常码";
        };
    }

    // Modbus 异常响应（功能码最高位置 1）
    @Getter
    public static class ModbusException extends IOException {
        private final int exceptionCode;

        public ModbusException(int exceptionCode, String message) {
            super(message);
            this.exceptionCode = exceptionCode;
        }

    }
}