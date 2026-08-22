package com.aischool.server.service.excel;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Student;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 成绩 Excel：单科一表（学号/姓名/成绩），POI 直读直写 */
@Service
public class ExcelScoreHelper {

    public record ScoreRow(String studentNo, String name, BigDecimal score) {}

    /** 读首个工作表：跳过表头；学号与成绩均空的行跳过；成绩兼容数值/文本单元格 */
    public List<ScoreRow> read(InputStream in) {
        List<ScoreRow> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter();
            for (Row row : wb.getSheetAt(0)) {
                if (row.getRowNum() == 0) {
                    continue; // 表头
                }
                String no = cell(fmt, row, 0);
                String name = cell(fmt, row, 1);
                String score = cell(fmt, row, 2);
                if (no.isBlank() && score.isBlank()) {
                    continue;
                }
                BigDecimal s = null;
                if (!score.isBlank()) {
                    try {
                        s = new BigDecimal(score.trim());
                    } catch (NumberFormatException e) {
                        throw new BizException(400, "第" + (row.getRowNum() + 1) + "行成绩不是数字: " + score);
                    }
                }
                rows.add(new ScoreRow(no.trim(), name.trim(), s));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "Excel 解析失败（需 .xlsx）: " + e.getMessage());
        }
        return rows;
    }

    private String cell(DataFormatter fmt, Row row, int i) {
        var c = row.getCell(i);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    /** 生成导入模板：表头 + 班级名册（学号/姓名预填，成绩留空） */
    public byte[] template(List<Student> roster) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("成绩导入");
            var head = sheet.createRow(0);
            head.createCell(0).setCellValue("学号");
            head.createCell(1).setCellValue("姓名");
            head.createCell(2).setCellValue("成绩");
            int i = 1;
            for (Student st : roster) {
                var row = sheet.createRow(i++);
                row.createCell(0).setCellValue(st.getStudentNo());
                row.createCell(1).setCellValue(st.getName());
            }
            for (int col = 0; col < 3; col++) {
                sheet.setColumnWidth(col, 16 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "模板生成失败: " + e.getMessage());
        }
    }
}
