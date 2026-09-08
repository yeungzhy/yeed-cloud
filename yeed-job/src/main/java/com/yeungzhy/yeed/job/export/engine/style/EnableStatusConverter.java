package com.yeungzhy.yeed.job.export.engine.style;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.yeungzhy.yeed.common.core.enums.EnableStatusEnum;
import com.yeungzhy.yeed.job.export.engine.api.Exporter;

/**
 * {@link EnableStatusEnum} 与 Excel 单元格的转换器
 *
 * <p> 导出流程注册见 {@link Exporter#registerDefaultConverter}。
 * 写方向输出中文描述（"启用" / "禁用"）而非 code，导出的列脱离字典即可读懂；
 * 读方向按 code 数值文本反解：两方向不对称，当前只有写方向被使用
 *
 * @author yeungzhy
 * @since 2026-09-07
 * @see EnableStatusEnum
 */
public class EnableStatusConverter implements Converter<EnableStatusEnum> {

    /** 本转换器支持的 Java 类型，EasyExcel 据此匹配字段 */
    @Override
    public Class<EnableStatusEnum> supportJavaTypeKey() {
        return EnableStatusEnum.class;
    }

    /**
     * 枚举写为中文 desc 文本而非 code 数字，便于直接阅读
     *
     * @param value               待导出枚举，非空
     * @param contentProperty     目标字段的 Excel 属性，本转换器未使用
     * @param globalConfiguration EasyExcel 全局配置，本转换器未使用
     * @return 写单元格的文本数据
     */
    @Override
    public WriteCellData<?> convertToExcelData(EnableStatusEnum value,
                                               ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        return new WriteCellData<>(value.getDesc());
    }

    /**
     * 单元格文本按 code 数值反解为枚举
     *
     * @param cellData             单元格数据，文本经 {@code getStringValue()} 取出
     * @param contentProperty      目标字段的 Excel 属性，本转换器未使用
     * @param globalConfiguration  EasyExcel 全局配置，本转换器未使用
     * @return 解析后的枚举
     * @throws NumberFormatException    单元格文本不是数字文本
     * @throws IllegalArgumentException 数字合法但不在 {@link EnableStatusEnum} 取值范围内
     */
    @Override
    public EnableStatusEnum convertToJavaData(ReadCellData<?> cellData,
                                              ExcelContentProperty contentProperty,
                                              GlobalConfiguration globalConfiguration) {
        String stringValue = cellData.getStringValue();
        return EnableStatusEnum.parse(Integer.valueOf(stringValue));
    }

}
