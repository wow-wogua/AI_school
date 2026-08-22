package com.aischool.server.service.ai;

import com.aischool.server.common.BizException;
import com.aischool.server.common.Exported;
import com.aischool.server.entity.Clazz;
import com.aischool.server.entity.Comment;
import com.aischool.server.entity.Student;
import com.aischool.server.entity.Term;
import com.aischool.server.mapper.ClazzMapper;
import com.aischool.server.mapper.CommentMapper;
import com.aischool.server.mapper.StudentMapper;
import com.aischool.server.mapper.TermMapper;
import com.aischool.server.security.UserPrincipal;
import com.aischool.server.service.auth.DataScopeService;
import com.aischool.server.service.excel.ExcelScoreHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 草稿：学业分析 / 班主任寄语 / 成长总结。
 * 硬数字全部来自 RuleFactsService（规则引擎）；LLM 只组织语言；
 * LLM 未配置 → 降级为确定性模板草稿。草稿均落 t_comment（AI草稿态），教师改后保存生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDraftService {

    private final AiClient aiClient;
    private final RuleFactsService factsService;
    private final CommentMapper commentMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final TermMapper termMapper;
    private final DataScopeService dataScope;
    private final ExcelScoreHelper excel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_TEACHER = "你是一位经验丰富、温和严谨的中学班主任。"
            + "写作时只允许使用用户提供的数据，严禁编造或修改任何数字与事实；"
            + "语气真诚具体，避免空话套话；不出现「根据数据显示」这类机械表述；"
            + "输出纯文本，禁止使用任何 Markdown 标记（如**加粗、#标题、-列表）。";

    // 提示词外置（留空 = 内置默认）：调话术只需改 application.yml / 环境变量，无需改代码重发版
    @Value("${aischool.ai.prompts.system-teacher:}")
    private String cfgSystemTeacher;
    @Value("${aischool.ai.prompts.comment-instruction:}")
    private String cfgCommentInstruction;
    @Value("${aischool.ai.prompts.summary-instruction:}")
    private String cfgSummaryInstruction;

    private String systemTeacher() {
        return cfgSystemTeacher == null || cfgSystemTeacher.isBlank() ? SYSTEM_TEACHER : cfgSystemTeacher;
    }

    private String commentInstruction() {
        return cfgCommentInstruction == null || cfgCommentInstruction.isBlank()
                ? "请根据以下数据为该学生写本学期班主任寄语（150~300字）："
                + "先肯定具体亮点，再中肯指出 1 个待改进点并给出期望，结尾鼓励。不要罗列全部数字，挑选关键事实。"
                : cfgCommentInstruction;
    }

    private String summaryInstruction() {
        return cfgSummaryInstruction == null || cfgSummaryInstruction.isBlank()
                ? "请根据以下数据为该学生生成本学期成长总结，分四块输出，每块 2~4 句："
                + "\n本学期亮点：\n学习发展：\n综合素质发展：\n下一阶段建议：\n"
                + "要求避免只看成绩，结合九格综合素质表现。"
                : cfgSummaryInstruction;
    }

    // ───────────────── 学业分析（结构化，规则为主） ─────────────────

    public Map<String, Object> analysis(Long studentId, Long termId) {
        Map<String, Object> facts = factsService.facts(studentId, termId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scores = (List<Map<String, Object>>) facts.get("期末成绩");
        if (scores == null || scores.isEmpty()) {
            throw new BizException(400, "该学期暂无考试成绩，无法分析");
        }
        Map<String, Object> best = scores.get(0);
        Map<String, Object> worst = scores.get(scores.size() - 1);
        String advantage = String.valueOf(facts.getOrDefault("优势学科", best.get("科目")));
        String toImprove = String.valueOf(facts.getOrDefault("待提升学科", worst.get("科目")));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("advantage", advantage + "、表现稳定" + (advantage.contains(String.valueOf(best.get("科目")))
                ? "，且为本学期最高分科目（" + best.get("得分") + "分）" : ""));
        m.put("toImprove", toImprove + "（本学期 " + worst.get("得分") + " 分，班级最高 "
                + (worst.get("班级最高") != null ? worst.get("班级最高") : "-") + " 分）");
        m.put("trend", "总分 " + facts.get("总分") + " 分，班级最高 " + facts.get("班级总分最高") + " 分，"
                + "年级最高 " + facts.get("年级总分最高") + " 分");
        List<String> alerts = new ArrayList<>();
        for (Map<String, Object> row : scores) {
            Object classMax = row.get("班级最高");
            if (classMax instanceof Number max && row.get("得分") instanceof Number mine) {
                double gap = max.doubleValue() - mine.doubleValue();
                if (gap >= 15) {
                    alerts.add(row.get("科目") + " 与班级最高相差 " + Math.round(gap) + " 分，存在提升空间");
                }
            }
        }
        m.put("alerts", alerts);
        return m;
    }

    // ───────────────── 班主任寄语（150~300 字草稿） ─────────────────

    public Map<String, Object> commentDraft(Long studentId, Long termId) {
        Map<String, Object> facts = factsService.facts(studentId, termId);
        String draft;
        String source;
        AiClient.ChatResult llmResult = null;
        if (aiClient.enabled()) {
            try {
                String user = commentInstruction() + "\n\n" + toJson(facts);
                llmResult = aiClient.chatWithUsage(systemTeacher(), user);
                draft = stripMd(llmResult.content()); // 提示词已禁 Markdown，此处兜底清洗残留
                source = "llm";
            } catch (Exception e) {
                log.warn("LLM 寄语生成失败，降级模板: {}", e.getMessage());
                draft = templateComment(facts);
                source = "template";
            }
        } else {
            draft = templateComment(facts);
            source = "template";
        }

        Comment comment = upsertComment(studentId, termId);
        comment.setAiDraft(draft);
        if (!"已确认".equals(comment.getStatus()) && !"已修改".equals(comment.getStatus())) {
            comment.setStatus("AI草稿");
        }
        commentMapper.updateById(comment);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("draft", draft);
        m.put("source", source);
        m.put("status", comment.getStatus());
        if (llmResult != null) {
            m.put("promptTokens", llmResult.promptTokens());
            m.put("completionTokens", llmResult.completionTokens());
        }
        return m;
    }

    /** 查询寄语当前状态 */
    public Map<String, Object> getComment(Long studentId, Long termId) {
        Comment comment = commentMapper.selectOne(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStudentId, studentId).eq(Comment::getTermId, termId)
                .eq(Comment::getType, "班主任").orderByDesc(Comment::getId).last("LIMIT 1"));
        Map<String, Object> m = new LinkedHashMap<>();
        if (comment != null) {
            m.put("content", comment.getContent());
            m.put("aiDraft", comment.getAiDraft());
            m.put("status", comment.getStatus());
        } else {
            m.put("content", "");
            m.put("aiDraft", "");
            m.put("status", "无");
        }
        return m;
    }

    /** 教师编辑保存（AI 只产草稿，人工确认后生效并用于报告） */
    public Map<String, Object> saveComment(Long studentId, Long termId, String content, boolean confirm) {
        if (content == null || content.isBlank()) {
            throw new BizException(400, "寄语内容不能为空");
        }
        Comment comment = upsertComment(studentId, termId);
        comment.setContent(content);
        comment.setStatus(confirm ? "已确认" : "已修改");
        commentMapper.updateById(comment);
        return getComment(studentId, termId);
    }

    /** 寄语导出（管理员或该班班主任）：班级×学期 → xlsx（学号/姓名/状态/寄语内容/AI 草稿） */
    public Exported exportComments(UserPrincipal user, Long classId, Long termId) {
        dataScope.checkClassOperable(user, classId);
        List<Student> roster = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .eq(Student::getClassId, classId).eq(Student::getStatus, "在读")
                .orderByAsc(Student::getStudentNo));
        Map<Long, Comment> comments = roster.isEmpty() ? Map.of()
                : commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getTermId, termId).eq(Comment::getType, "班主任")
                        .in(Comment::getStudentId, roster.stream().map(Student::getId).toList()))
                .stream().collect(java.util.stream.Collectors.toMap(
                        Comment::getStudentId, c -> c, (a, b) -> a));
        List<Object[]> rows = new ArrayList<>();
        for (Student st : roster) {
            Comment c = comments.get(st.getId());
            rows.add(new Object[]{st.getStudentNo(), st.getName(),
                    c == null ? "无" : c.getStatus(),
                    c == null || c.getContent() == null ? "" : c.getContent(),
                    c == null || c.getAiDraft() == null ? "" : c.getAiDraft()});
        }
        Clazz clazz = clazzMapper.selectById(classId);
        Term term = termMapper.selectById(termId);
        String name = "寄语_" + (clazz != null ? clazz.getName() : classId) + "_"
                + (term != null ? term.getName() : termId) + ".xlsx";
        return new Exported(name, excel.export("寄语",
                new String[]{"学号", "姓名", "状态", "寄语内容", "AI 草稿"}, rows));
    }

    // ───────────────── 成长总结（四块） ─────────────────

    public Map<String, Object> summaryDraft(Long studentId, Long termId) {
        Map<String, Object> facts = factsService.facts(studentId, termId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (aiClient.enabled()) {
            try {
                String user = summaryInstruction() + "\n\n" + toJson(facts);
                AiClient.ChatResult cr = aiClient.chatWithUsage(systemTeacher(), user);
                String text = stripMd(cr.content());
                m.put("raw", text);
                m.put("blocks", parseBlocks(text));
                m.put("source", "llm");
                m.put("promptTokens", cr.promptTokens());
                m.put("completionTokens", cr.completionTokens());
                return m;
            } catch (Exception e) {
                log.warn("LLM 总结生成失败，降级模板: {}", e.getMessage());
            }
        }
        m.put("raw", templateSummary(facts));
        m.put("blocks", parseBlocks(m.get("raw") + ""));
        m.put("source", "template");
        return m;
    }

    // ───────────────── 内部 ─────────────────

    private Comment upsertComment(Long studentId, Long termId) {
        Comment comment = commentMapper.selectOne(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStudentId, studentId).eq(Comment::getTermId, termId)
                .eq(Comment::getType, "班主任").orderByDesc(Comment::getId).last("LIMIT 1"));
        if (comment == null) {
            comment = new Comment();
            comment.setStudentId(studentId);
            comment.setTermId(termId);
            comment.setType("班主任");
            comment.setStatus("AI草稿");
            commentMapper.insert(comment);
        }
        return comment;
    }

    private String templateComment(Map<String, Object> facts) {
        StringBuilder sb = new StringBuilder();
        sb.append(facts.get("姓名")).append("同学：本学期你的综合表现可圈可点。");
        sb.append("学业上，总分 ").append(facts.get("总分")).append(" 分");
        Object classMax = facts.get("班级总分最高");
        if (classMax != null) {
            sb.append("（班级最高 ").append(classMax).append(" 分）");
        }
        sb.append("，其中").append(facts.getOrDefault("优势学科", "")).append("表现突出，值得保持；")
                .append(facts.getOrDefault("待提升学科", "")).append("仍有提升空间，建议针对性补强。");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grids = (List<Map<String, Object>>) facts.get("九格表现");
        if (grids != null && !grids.isEmpty()) {
            Map<String, Object> top = grids.stream()
                    .max((a, b) -> Double.compare(((Number) a.get("获得评价积分")).doubleValue(),
                            ((Number) b.get("获得评价积分")).doubleValue())).orElse(null);
            if (top != null) {
                sb.append("综合素质方面，").append(top.get("维度")).append("表现最为亮眼（累计评价积分 ")
                        .append(top.get("获得评价积分")).append("），全学期共获得 ")
                        .append(facts.get("学期累计被评价次数")).append(" 次教师评价，成长可见。");
            }
        }
        sb.append("新的学期，愿你保持热情与专注，查漏补缺、稳步前行，成为更好的自己。加油！");
        return sb.toString();
    }

    private String templateSummary(Map<String, Object> facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("本学期亮点：").append(facts.get("姓名")).append("同学本学期获得 ")
                .append(facts.get("学期累计被评价次数")).append(" 次教师评价，")
                .append(facts.getOrDefault("优势学科", "")).append("表现稳定突出。\n");
        sb.append("学习发展：期末总分 ").append(facts.get("总分")).append(" 分，")
                .append(facts.getOrDefault("待提升学科", "")).append("有提升空间，需加强基础巩固。\n");
        sb.append("综合素质发展：九格评价显示 ")
                .append(gridTopName(facts)).append("维度积分领先，德智体美劳全面发展态势良好。\n");
        sb.append("下一阶段建议：保持优势科目的学习节奏，针对薄弱科目制定周计划并主动请教老师；"
                + "继续参与班级活动，在集体中锻炼组织与表达能力。");
        return sb.toString();
    }

    private String gridTopName(Map<String, Object> facts) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> grids = (List<Map<String, Object>>) facts.get("九格表现");
        if (grids == null || grids.isEmpty()) {
            return "";
        }
        return grids.stream().max((a, b) -> Double.compare(
                        ((Number) a.get("获得评价积分")).doubleValue(),
                        ((Number) b.get("获得评价积分")).doubleValue()))
                .map(g -> String.valueOf(g.get("维度"))).orElse("") + "、";
    }

    /** 解析四块：兼容「标题：内容同行」与「标题独占一行、内容跨多行」两种 LLM 输出格式 */
    private Map<String, String> parseBlocks(String text) {
        Map<String, String> blocks = new LinkedHashMap<>();
        List<String> keys = List.of("本学期亮点", "学习发展", "综合素质发展", "下一阶段建议");
        String current = null;
        StringBuilder val = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String clean = line.replaceFirst("^[#>\\s*\\-•·]+", ""); // 剥 Markdown/项目符号前缀再匹配
            String hit = keys.stream().filter(clean::startsWith).findFirst().orElse(null);
            if (hit != null) {
                if (current != null) blocks.put(current, val.toString().trim());
                current = hit;
                val = new StringBuilder(clean.substring(hit.length()).replaceFirst("^[:：]\\s*", "").trim());
            } else if (current != null && !clean.isBlank()) {
                if (val.length() > 0) val.append('\n');
                val.append(clean);
            }
        }
        if (current != null) blocks.put(current, val.toString().trim());
        return blocks;
    }

    /** 清洗 LLM 输出中的加粗/标题残留（提示词已禁止，双保险） */
    private String stripMd(String s) {
        return s == null ? s : s.replace("**", "");
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
