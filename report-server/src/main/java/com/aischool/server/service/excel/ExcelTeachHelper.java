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

/** 任课关系 Excel：批量导入（教师+班级+学科），POI 直读直写，列序与 template() 一致 */
@Service
public class ExcelTeachHelper {

    public record TeachRow(int rowNum, String teacher, String className, String subjectName) {}

    /** 读首个工作表：跳过表头；三列全空的行跳过 */
    public List<TeachRow> read(InputStream in) {
        List<TeachRow> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter();
            for (Row row : wb.getSheetAt(0)) {
                if (row.getRowNum() == 0) {
                    continue; // 表头
                }
                String teacher = cell(fmt, row, 0);
                String className = cell(fmt, row, 1);
                String subjectName = cell(fmt, row, 2);
                if (teacher.isBlank() && className.isBlank() && subjectName.isBlank()) {
                    continue;
                }
                rows.add(new TeachRow(row.getRowNum() + 1, teacher, className, subjectName));
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
            var sheet = wb.createSheet("任课导入");
            String[] headers = {"教师(登录账号或姓名)(必填)", "班级名称(必填)", "任教学科(必填)"};
            var head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 24 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "生成模板失败: " + e.getMessage());
        }
    }
}
