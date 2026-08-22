# -*- coding: utf-8 -*-
"""按 deploy/schema.sql 生成 MyBatis-Plus 实体 + Mapper（报表服务数据层脚手架）。
表结构变化时重跑：PYTHONIOENCODING=utf-8 python gen_entities.py
"""
import os

BASE = os.path.join(os.path.dirname(__file__), '..',
                    'src', 'main', 'java', 'com', 'aischool', 'server')

# (Entity, table, [(field, javaType)])
IMPORTS = {
    'BigDecimal': 'import java.math.BigDecimal;',
    'LocalDate': 'import java.time.LocalDate;',
    'LocalDateTime': 'import java.time.LocalDateTime;',
}

TABLES = [
    ('User', 't_user', [
        ('id', 'Long'), ('username', 'String'), ('passwordHash', 'String'),
        ('realName', 'String'), ('role', 'String'), ('phone', 'String'),
        ('status', 'Integer'), ('createTime', 'LocalDateTime'), ('updateTime', 'LocalDateTime')]),
    ('Grade', 't_grade', [
        ('id', 'Long'), ('name', 'String'), ('schoolYear', 'String'),
        ('createTime', 'LocalDateTime'), ('updateTime', 'LocalDateTime')]),
    ('Clazz', 't_class', [
        ('id', 'Long'), ('gradeId', 'Long'), ('name', 'String'),
        ('headTeacherId', 'Long'), ('createTime', 'LocalDateTime'), ('updateTime', 'LocalDateTime')]),
    ('Student', 't_student', [
        ('id', 'Long'), ('userId', 'Long'), ('studentNo', 'String'), ('name', 'String'),
        ('gender', 'String'), ('classId', 'Long'), ('enrollDate', 'LocalDate'),
        ('status', 'String'), ('photoUrl', 'String'), ('guardianName', 'String'),
        ('guardianPhone', 'String')]),
    ('Term', 't_term', [
        ('id', 'Long'), ('name', 'String'), ('startDate', 'LocalDate'),
        ('endDate', 'LocalDate'), ('isCurrent', 'Integer')]),
    ('Subject', 't_subject', [
        ('id', 'Long'), ('name', 'String'), ('shortName', 'String'), ('type', 'String'),
        ('sort', 'Integer'), ('regularSort', 'Integer'), ('motto', 'String'),
        ('procHMin', 'BigDecimal'), ('procHMax', 'BigDecimal'), ('procHStep', 'BigDecimal'),
        ('procWMax', 'BigDecimal')]),
    ('Teach', 't_teach', [
        ('id', 'Long'), ('teacherId', 'Long'), ('classId', 'Long'), ('subjectId', 'Long')]),
    ('Exam', 't_exam', [
        ('id', 'Long'), ('termId', 'Long'), ('name', 'String'), ('examDate', 'LocalDate'),
        ('classMaxTotal', 'BigDecimal'), ('gradeMaxTotal', 'BigDecimal')]),
    ('ExamSubject', 't_exam_subject', [
        ('id', 'Long'), ('examId', 'Long'), ('subjectId', 'Long'),
        ('fullScore', 'BigDecimal'), ('classMax', 'BigDecimal'), ('gradeMax', 'BigDecimal')]),
    ('Score', 't_score', [
        ('id', 'Long'), ('examId', 'Long'), ('subjectId', 'Long'), ('studentId', 'Long'),
        ('score', 'BigDecimal'), ('classRank', 'Integer'), ('gradeRank', 'Integer'),
        ('createdBy', 'Long')]),
    ('RegularScore', 't_regular_score', [
        ('id', 'Long'), ('studentId', 'Long'), ('subjectId', 'Long'), ('termId', 'Long'),
        ('score', 'BigDecimal'), ('date', 'LocalDate')]),
    ('HomeworkStat', 't_homework_stat', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('subjectId', 'Long'),
        ('colType', 'Integer'), ('score', 'BigDecimal'), ('times', 'Integer')]),
    ('Grid', 't_grid', [
        ('id', 'Long'), ('code', 'String'), ('name', 'String'), ('icon', 'String'),
        ('sort', 'Integer'), ('curAxisMax', 'BigDecimal'), ('curAxisStep', 'BigDecimal'),
        ('prevAxisMax', 'BigDecimal'), ('prevAxisStep', 'BigDecimal'),
        ('weekMin', 'BigDecimal'), ('weekMax', 'BigDecimal'), ('weekStep', 'BigDecimal')]),
    ('Indicator', 't_indicator', [
        ('id', 'Long'), ('gridId', 'Long'), ('name', 'String'), ('direction', 'String'),
        ('defaultScore', 'BigDecimal'), ('subjectScope', 'String')]),
    ('Evaluation', 't_evaluation', [
        ('id', 'Long'), ('studentId', 'Long'), ('teacherId', 'Long'), ('indicatorId', 'Long'),
        ('title', 'String'), ('score', 'BigDecimal'), ('remark', 'String'),
        ('evalTime', 'LocalDateTime')]),
    ('GridStatWeek', 't_grid_stat_week', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('gridId', 'Long'),
        ('weekNo', 'Integer'), ('score', 'BigDecimal')]),
    ('GridStatTerm', 't_grid_stat_term', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('gridId', 'Long'),
        ('points', 'BigDecimal'), ('evalCount', 'Integer'), ('kindCount', 'Integer'),
        ('score', 'BigDecimal')]),
    ('ClassGridAvg', 't_class_grid_avg', [
        ('id', 'Long'), ('classId', 'Long'), ('termId', 'Long'), ('gridId', 'Long'),
        ('avgScore', 'BigDecimal')]),
    ('GradeGridAvg', 't_grade_grid_avg', [
        ('id', 'Long'), ('gradeId', 'Long'), ('termId', 'Long'), ('gridId', 'Long'),
        ('avgScore', 'BigDecimal')]),
    ('SubjectStatWeek', 't_subject_stat_week', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('subjectId', 'Long'),
        ('weekNo', 'Integer'), ('mine', 'BigDecimal'), ('classAvg', 'BigDecimal'),
        ('gradeAvg', 'BigDecimal')]),
    ('SubjectStatTerm', 't_subject_stat_term', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('subjectId', 'Long'),
        ('posMine', 'BigDecimal'), ('posClassAvg', 'BigDecimal'), ('posGradeAvg', 'BigDecimal'),
        ('negMine', 'BigDecimal'), ('negClassAvg', 'BigDecimal'), ('negGradeAvg', 'BigDecimal')]),
    ('ProcessWeek', 't_process_week', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('weekNo', 'Integer'),
        ('mine', 'BigDecimal'), ('classAvg', 'BigDecimal'), ('gradeAvg', 'BigDecimal')]),
    ('ProcessStat', 't_process_stat', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'),
        ('posMine', 'BigDecimal'), ('posClassAvg', 'BigDecimal'), ('posGradeAvg', 'BigDecimal'),
        ('negMine', 'BigDecimal'), ('negClassAvg', 'BigDecimal'), ('negGradeAvg', 'BigDecimal')]),
    ('StudentAnalysis', 't_student_analysis', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('advantage', 'String'),
        ('toImprove', 'String'), ('radarAdvantages', 'String'), ('radarToImprove', 'String')]),
    ('Activity', 't_activity', [
        ('id', 'Long'), ('title', 'String'), ('type', 'String'), ('startTime', 'LocalDateTime'),
        ('place', 'String'), ('coverUrl', 'String'), ('intro', 'String'), ('creatorId', 'Long')]),
    ('ActivitySignup', 't_activity_signup', [
        ('id', 'Long'), ('activityId', 'Long'), ('studentId', 'Long'),
        ('signupTime', 'LocalDateTime'), ('checkinTime', 'LocalDateTime'),
        ('award', 'String'), ('performance', 'String'), ('evalText', 'String')]),
    ('Honor', 't_honor', [
        ('id', 'Long'), ('studentId', 'Long'), ('name', 'String'),
        ('level', 'String'), ('issuer', 'String'), ('honorDate', 'LocalDate'),
        ('fileUrl', 'String'), ('aiParsed', 'String'), ('confirmStatus', 'String'),
        ('createTime', 'LocalDateTime')]),
    ('CoinAccount', 't_coin_account', [
        ('id', 'Long'), ('studentId', 'Long'), ('currentCoin', 'BigDecimal'),
        ('totalCoin', 'BigDecimal'), ('updateTime', 'LocalDateTime')]),
    ('CoinRate', 't_coin_rate', [
        ('id', 'Long'), ('rate', 'BigDecimal'), ('effectiveDate', 'LocalDate')]),
    ('CoinIncome', 't_coin_income', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('sourceType', 'String'),
        ('sourceId', 'Long'), ('module', 'String'), ('score', 'BigDecimal'),
        ('coin', 'BigDecimal'), ('displayOrder', 'Integer'), ('createTime', 'LocalDateTime')]),
    ('CoinExpense', 't_coin_expense', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('item', 'String'),
        ('coin', 'BigDecimal'), ('createTime', 'LocalDateTime')]),
    ('CoinWeek', 't_coin_week', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('weekNo', 'Integer'),
        ('inMine', 'BigDecimal'), ('inClass', 'BigDecimal'), ('inGrade', 'BigDecimal'),
        ('outMine', 'BigDecimal'), ('outClass', 'BigDecimal'), ('outGrade', 'BigDecimal')]),
    ('CoinStat', 't_coin_stat', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'),
        ('compareClassAvg', 'String'), ('compareGradeAvg', 'String')]),
    ('GrowthLevel', 't_growth_level', [
        ('id', 'Long'), ('level', 'Integer'), ('minScore', 'BigDecimal'),
        ('symbolName', 'String'), ('symbolImg', 'String')]),
    ('GrowthSymbolStat', 't_growth_symbol_stat', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('score', 'BigDecimal')]),
    ('Comprehensive', 't_comprehensive', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('moral', 'String'),
        ('ability', 'String'), ('health', 'String'), ('aesthetic', 'String'),
        ('practice', 'String'), ('finalLevel', 'String')]),
    ('Comment', 't_comment', [
        ('id', 'Long'), ('studentId', 'Long'), ('termId', 'Long'), ('type', 'String'),
        ('content', 'String'), ('aiDraft', 'String'), ('status', 'String'),
        ('createTime', 'LocalDateTime'), ('updateTime', 'LocalDateTime')]),
    ('ReportTemplate', 't_report_template', [
        ('id', 'Long'), ('schoolName', 'String'), ('sections', 'String'),
        ('status', 'String'), ('createTime', 'LocalDateTime'), ('updateTime', 'LocalDateTime')]),
    ('ReportTask', 't_report_task', [
        ('id', 'Long'), ('termId', 'Long'), ('scope', 'String'), ('targetId', 'Long'),
        ('status', 'String'), ('total', 'Integer'), ('done', 'Integer'), ('failed', 'Integer'),
        ('createBy', 'Long'), ('createTime', 'LocalDateTime'), ('updateTime', 'LocalDateTime')]),
    ('Report', 't_report', [
        ('id', 'Long'), ('taskId', 'Long'), ('studentId', 'Long'), ('termId', 'Long'),
        ('fileUrl', 'String'), ('pageCount', 'Integer'), ('genTime', 'LocalDateTime'),
        ('status', 'String'), ('error', 'String'), ('createTime', 'LocalDateTime')]),
]


def entity_java(name, table, fields):
    needed = {imp for _, typ in fields if typ in IMPORTS for imp in [typ]}
    imports = ''.join(f'{IMPORTS[t]}\n' for t in ['BigDecimal', 'LocalDate', 'LocalDateTime'] if t in needed)
    decl = '\n'.join(f'    private {typ} {field};' for field, typ in fields)
    return f'''package com.aischool.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
{imports}
/** {table} */
@Data
@TableName("{table}")
public class {name} {{

    @TableId(type = IdType.AUTO)
{decl}
}}
'''


def mapper_java(name):
    return f'''package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.{name};

public interface {name}Mapper extends BaseMapper<{name}> {{
}}
'''


def main():
    for name, table, fields in TABLES:
        for d in ('entity', 'mapper'):
            os.makedirs(os.path.join(BASE, d), exist_ok=True)
        with open(os.path.join(BASE, 'entity', f'{name}.java'), 'w', encoding='utf-8', newline='\n') as fp:
            fp.write(entity_java(name, table, fields))
        with open(os.path.join(BASE, 'mapper', f'{name}Mapper.java'), 'w', encoding='utf-8', newline='\n') as fp:
            fp.write(mapper_java(name))
    print(f'generated {len(TABLES)} entities + mappers')


if __name__ == '__main__':
    main()
