package com.aischool.server.service.excel;

import com.aischool.server.common.BizException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** 新生 Excel：批量建档（学号/姓名/性别/班级名称/家长姓名/家长电话），POI 直读直写 */
@Service
public class ExcelStudentHelper {

    public record StudentRow(int rowNum, String studentNo, String name, String gender,
                             String className, String guardianName, String guardianPhone) {}

    /** 读首个工作表：跳过表头；学号与姓名均空的行跳过 */
    public List<StudentRow> read(InputStream in) {
        List<StudentRow> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter();
            for (Row row : wb.getSheetAt(0)) {
                if (row.getRowNum() == 0) {
                    continue; // 表头
                }
                String no = cell(fmt, row, 0);
                String name = cell(fmt, row, 1);
                if (no.isBlank() && name.isBlank()) {
                    continue;
                }
                rows.add(new StudentRow(row.getRowNum() + 1, no, name, cell(fmt, row, 2),
                        cell(fmt, row, 3), cell(fmt, row, 4), cell(fmt, row, 5)));
            }
        } catch (Exception e) {
            throw new BizException(400, "Excel 解析失败（需 .xlsx）: " + e.getMessage());
        }
        return rows;
    }

    private String cell(DataFormatter fmt, Row row, int i) {
        var c = row.getCell(i);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    /** 生成导入模板：仅表头（不放示例行，避免演示数据被误导入） */
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("新生导入");
            String[] headers = {"学号", "姓名", "性别(男/女)", "班级名称", "家长姓名", "家长电话"};
            var head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 16 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "模板生成失败: " + e.getMessage());
        }
    }
}
