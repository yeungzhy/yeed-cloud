package com.yeungzhy.yeed.gateway.filter.openapi;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;

/**
 * DataBuffer 读取工具（加解密需要完整字节，且必须显式释放池化缓冲）
 *
 * @author yeungzhy
 * @since 2026-09-10
 */
public final class DataBufferReadUtil {
    private DataBufferReadUtil() {
        throw new UnsupportedOperationException("DataBufferReadUtil is a utility class and cannot be instantiated");
    }

    /**
     * 读尽缓冲区并释放，避免池化内存泄漏
     *
     * @param dataBuffer 待读缓冲区
     * @return 内容字节
     */
    public static byte[] readAndRelease(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return bytes;
    }


}
