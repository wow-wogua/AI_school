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

/** 教师 Excel：批量建号（账号/角色 + 档案字段），POI 直读直写，列序与 template() 一致 */
@Service
public class ExcelTeacherHelper {

    public record TeacherRow(int rowNum, String username, String realName, String role, String phone,
                             String employeeNo, String gender, String subjectName, String title, String duty,
                             String teachingYears, String hireDate, String headClassName, String intro) {}

    /** 读首个工作表：跳过表头；账号与姓名均空的行跳过 */
    public List<TeacherRow> read(InputStream in) {
        List<TeacherRow> rows = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            DataFormatter fmt = new DataFormatter();
            for (Row row : wb.getSheetAt(0)) {
                if (row.getRowNum() == 0) {
                    continue; // 表头
                }
                String username = cell(fmt, row, 0);
                String realName = cell(fmt, row, 1);
                if (username.isBlank() && realName.isBlank()) {
                    continue;
                }
                rows.add(new TeacherRow(row.getRowNum() + 1, username, realName, cell(fmt, row, 2),
                        cell(fmt, row, 3), cell(fmt, row, 4), cell(fmt, row, 5), cell(fmt, row, 6),
                        cell(fmt, row, 7), cell(fmt, row, 8), cell(fmt, row, 9), cell(fmt, row, 10),
                        cell(fmt, row, 11), cell(fmt, row, 12)));
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
            var sheet = wb.createSheet("教师导入");
            String[] headers = {"账号(必填)", "姓名(必填)", "角色(管理员/班主任/教师)(必填)", "手机号",
                    "工号", "性别(男/女)", "任教学科", "职称", "职务", "教龄(数字)",
                    "入职年月(2026-08-30)", "班主任所带班级", "简介"};
            var head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 18 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(500, "生成模板失败: " + e.getMessage());
        }
    }
}
