package com.yeungzhy.yeed.common.web.config;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import com.yeungzhy.yeed.common.core.constant.Constant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * FastJson2 全局配置
 * 解决 Java 8+ 时间类型格式化问题，保持与 Jackson 配置一致
 *
 * @author yeungzhy
 */
@AutoConfiguration
public class FastJson2AutoConfiguration implements ApplicationRunner {


    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 获取 FastJson2 全局的 Provider
        ObjectWriterProvider provider = JSONFactory.getDefaultObjectWriterProvider();

        // 注册 LocalDateTime 序列化器
        provider.register(LocalDateTime.class, new ObjectWriter<LocalDateTime>() {
            final DateTimeFormatter dtf = DateTimeFormatter.ofPattern(Constant.DATE_TIME_PATTERN);
            @Override
            public void write(com.alibaba.fastjson2.JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
                if (object == null) {
                    jsonWriter.writeNull();
                    return;
                }
                LocalDateTime localDateTime = (LocalDateTime) object;
                jsonWriter.writeString(localDateTime.format(dtf));
            }
        });

        // 注册 LocalDate 序列化器
        provider.register(LocalDate.class, new ObjectWriter<LocalDate>() {
            final DateTimeFormatter df = DateTimeFormatter.ofPattern(Constant.DATE_PATTERN);
            @Override
            public void write(com.alibaba.fastjson2.JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
                if (object == null) {
                    jsonWriter.writeNull();
                    return;
                }
                LocalDate localDate = (LocalDate) object;
                jsonWriter.writeString(localDate.format(df));
            }
        });

        // 注册 LocalTime 序列化器
        provider.register(LocalTime.class, new ObjectWriter<LocalTime>() {
            final DateTimeFormatter tf = DateTimeFormatter.ofPattern(Constant.TIME_PATTERN);
            @Override
            public void write(com.alibaba.fastjson2.JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
                if (object == null) {
                    jsonWriter.writeNull();
                    return;
                }
                LocalTime localTime = (LocalTime) object;
                jsonWriter.writeString(localTime.format(tf));
            }
        });

        // 注册 Date 序列化器
        provider.register(Date.class, new ObjectWriter<Date>() {
            final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(Constant.DATE_TIME_PATTERN);
            @Override
            public void write(com.alibaba.fastjson2.JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
                if (object == null) {
                    jsonWriter.writeNull();
                    return;
                }
                Date date = (Date) object;
                jsonWriter.writeString(sdf.format(date));
            }
        });
    }


}
