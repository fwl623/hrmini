package com.company.hrms.common.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Excel 导出工具类
 * <p>
 * 基于 Alibaba EasyExcel 封装，生成 Excel 字节数组，由 Controller 层包装响应。
 * </p>
 */
@Slf4j
public final class ExcelExportUtil {

    private ExcelExportUtil() {}

    /**
     * 生成 Excel 文件字节数组
     *
     * @param sheetName Sheet 名称
     * @param data      导出的数据列表
     * @param clazz     数据类（需使用 @ExcelProperty 注解标记字段）
     * @param <T>       数据类型
     * @return Excel 文件字节数组
     */
    public static <T> byte[] generateExcelBytes(String sheetName, List<T> data, Class<T> clazz) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            EasyExcel.write(baos, clazz)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .sheet(sheetName)
                    .doWrite(data);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Excel 生成失败", e);
            throw new RuntimeException("生成 Excel 失败", e);
        }
    }
}
