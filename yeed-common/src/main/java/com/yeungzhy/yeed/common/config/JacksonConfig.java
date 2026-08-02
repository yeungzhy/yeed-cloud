package com.yeungzhy.yeed.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import static com.yeungzhy.yeed.common.Constant.*;

/**
 * Jackson 全局配置
 *
 * @author yeungzhy
 */
@Configuration
public class JacksonConfig {

    /** 统一日期时间格式 */
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final DateTimeFormatter df = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final DateTimeFormatter tf = DateTimeFormatter.ofPattern(TIME_PATTERN);

    /**
     * 自定义 ObjectMapper 替换 Spring Boot 默认实例, 覆盖默认行为
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // java.util.Date 时间类型(高并发注意 SimpleDateFormat 线程安全问题)
        objectMapper.setDateFormat(new SimpleDateFormat(DATE_TIME_PATTERN));
        objectMapper.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // Java 8+ 时间类型
        objectMapper.registerModule(new JavaTimeModule()
                // 序列化（对象 -> JSON）
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtf))
                .addSerializer(LocalDate.class, new LocalDateSerializer(df))
                .addSerializer(LocalTime.class, new LocalTimeSerializer(tf))
                // 反序列化（JSON -> 对象），保持与序列化一致
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dtf))
                .addDeserializer(LocalDate.class, new LocalDateDeserializer(df))
                .addDeserializer(LocalTime.class, new LocalTimeDeserializer(tf))
        );


        // Long 及 long 类型转 String  (解决雪花ID前端精度丢失问题)
        objectMapper.registerModule(new SimpleModule()
                .addSerializer(Long.class, ToStringSerializer.instance)
                .addSerializer(Long.TYPE, ToStringSerializer.instance)
        );

        // 忽略未知字段（当前端传入后端不存在的字段时不报错，提高容错性）
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 关闭输出: 时间戳\时区
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_WITH_ZONE_ID, false);

        return objectMapper;
    }

}
