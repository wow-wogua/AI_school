package com.aischool.server.service.oa;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.Goods;
import com.aischool.server.entity.GoodsFlow;
import com.aischool.server.entity.OaFlowLog;
import com.aischool.server.entity.OaForm;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.entity.User;
import com.aischool.server.mapper.GoodsFlowMapper;
import com.aischool.server.mapper.GoodsMapper;
import com.aischool.server.mapper.OaFlowLogMapper;
import com.aischool.server.mapper.OaFormMapper;
import com.aischool.server.mapper.SysConfigMapper;
import com.aischool.server.mapper.UserMapper;
import com.aischool.server.security.AuthUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OA 审批引擎（批9）：公章申请固定三级审批（原始需求节点名「一级/二级/三级审批」）、
 * 物资申领级数可配（t_sys_config，默认 1）。审批人配置在管理端（校方自助），
 * ADMIN 可代审任意待审级（审批人休假兜底）。
 */
@Service
@RequiredArgsConstructor
public class OaService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String NODE_SUBMIT = "提交";

    private final OaFormMapper formMapper;
    private final OaFlowLogMapper logMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsFlowMapper goodsFlowMapper;
    private final SysConfigMapper sysConfigMapper;
    private final UserMapper userMapper;

    // ---- 配置 ----

    private String cfg(String key) {
        SysConfig c = sysConfigMapper.selectById(key);
        return c == null || c.getCfgValue() == null ? "" : c.getCfgValue().trim();
    }

    public void setCfg(String key, String value) {
        SysConfig c = new SysConfig();
        c.setCfgKey(key);
        c.setCfgValue(value);
        sysConfigMapper.updateById(c); // 行由 V22 种子保证存在
    }

    /** 配置读取（管理端回显用；空配置返回 ""） */
    public String cfgOf(String key) {
        return cfg(key);
    }

    /** 物资/请假审批级数（1-3，默认 1，键 oa_{type}_levels）；公章固定 3 */
    public int levels(String formType) {
        if (OaForm.TYPE_SEAL.equals(formType)) {
            return 3;
        }
        try {
            return Math.max(1, Math.min(3, Integer.parseInt(cfg("oa_" + formType.toLowerCase() + "_levels"))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** 第 level 级审批人 user_id；未配置返回 null */
    private Long approverId(String formType, int level) {
        String v = cfg("oa_" + formType.toLowerCase() + "_l" + level);
        return v.isEmpty() ? null : Long.parseLong(v);
    }

    /** 提交前校验所用级数的审批人全部配齐（否则单据卡死在无人可审的级） */
    private void checkApproversConfigured(String formType) {
        int n = levels(formType);
        for (int i = 1; i <= n; i++) {
            Long id = approverId(formType, i);
            if (id == null || userMapper.selectById(id) == null) {
                throw new BizException(400, "管理员尚未配齐「" + typeName(formType) + "」第 " + i + " 级审批人，请联系管理员配置后再提交");
            }
        }
    }

    public static String typeName(String formType) {
        if (OaForm.TYPE_GOODS.equals(formType)) {
            return "物资申领";
        }
        return OaForm.TYPE_LEAVE.equals(formType) ? "教师请假" : "公章使用申请";
    }

    // ---- 提交 ----

    @Data
    public static class SubmitReq {
        private String formType;
        private String title;
        private String reason;
        private String useDate;
        private List<GoodsLine> goodsLines;
        private String leaveType; // 批10：事假/病假/婚假/产假/其他
        private String startDate;
        private String endDate;
    }

    @Data
    public static class GoodsLine {
        private Long goodsId;
        private Integer qty;
    }

    public OaForm submit(SubmitReq req) {
        var user = AuthUtil.current();
        if ("PARENT".equals(user.role())) {
            throw new BizException(403, "家长账号无需使用行政办公审批");
        }
        String type = OaForm.TYPE_LEAVE.equals(req.getFormType()) ? OaForm.TYPE_LEAVE
                : OaForm.TYPE_GOODS.equals(req.getFormType()) ? OaForm.TYPE_GOODS : OaForm.TYPE_SEAL;
        checkApproversConfigured(type);
        OaForm form = new OaForm();
        form.setFormType(type);
        form.setApplicantId(user.userId());
        form.setStatus(OaForm.PENDING);
        form.setCurrentLevel(1);
        if (type.equals(OaForm.TYPE_SEAL)) {
            if (req.getTitle() == null || req.getTitle().isBlank()) {
                throw new BizException(400, "请填写申请事由");
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", req.getTitle());
            detail.put("useDate", req.getUseDate());
            form.setTitle(req.getTitle().trim());
            form.setDetail(toJson(detail));
        } else if (type.equals(OaForm.TYPE_LEAVE)) {
            if (req.getLeaveType() == null || req.getLeaveType().isBlank()
                    || req.getStartDate() == null || req.getStartDate().isBlank()
                    || req.getEndDate() == null || req.getEndDate().isBlank()) {
                throw new BizException(400, "请填写请假类型与起止日期");
            }
            if (req.getEndDate().compareTo(req.getStartDate()) < 0) {
                throw new BizException(400, "结束日期不能早于开始日期");
            }
            if (req.getTitle() == null || req.getTitle().isBlank()) {
                throw new BizException(400, "请填写请假事由");
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("leaveType", req.getLeaveType());
            detail.put("startDate", req.getStartDate());
            detail.put("endDate", req.getEndDate());
            detail.put("reason", req.getTitle().trim());
            form.setTitle(req.getLeaveType() + "·" + req.getStartDate() + "~" + req.getEndDate());
            form.setDetail(toJson(detail));
        } else {
            List<GoodsLine> lines = req.getGoodsLines();
            if (lines == null || lines.isEmpty()) {
                throw new BizException(400, "请至少选择一项物资");
            }
            List<Map<String, Object>> detail = new ArrayList<>();
            StringBuilder title = new StringBuilder();
            for (GoodsLine l : lines) {
                Goods g = goodsMapper.selectById(l.getGoodsId());
                if (g == null || g.getStatus() == null || g.getStatus() != 1) {
                    throw new BizException(400, "物资不存在或已停用");
                }
                if (l.getQty() == null || l.getQty() <= 0) {
                    throw new BizException(400, "申领数量须大于 0");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("goodsId", g.getId());
                row.put("name", g.getName());
                row.put("qty", l.getQty());
                row.put("unit", g.getUnit());
                row.put("location", g.getLocation() == null ? "" : g.getLocation());
                detail.add(row);
                if (title.length() > 0) {
                    title.append("、");
                }
                title.append(g.getName()).append("×").append(l.getQty()).append(g.getUnit());
            }
            form.setTitle(title.toString());
            form.setDetail(toJson(detail));
        }
        formMapper.insert(form);
        OaFlowLog log = new OaFlowLog();
        log.setFormId(form.getId());
        log.setAction(OaFlowLog.SUBMIT);
        log.setLevel(0);
        log.setNodeName(NODE_SUBMIT);
        log.setOperatorId(user.userId());
        logMapper.insert(log);
        return form;
    }

    // ---- 查询 ----

    /** 我的申请 */
    public List<Map<String, Object>> myList(String status) {
        var user = AuthUtil.current();
        return toRows(formMapper.selectList(new LambdaQueryWrapper<OaForm>()
                .eq(OaForm::getApplicantId, user.userId())
                .eq(status != null && !status.isBlank(), OaForm::getStatus, status)
                .orderByDesc(OaForm::getId)));
    }

    /** 待我审批：当前待审级配置人=本人 的 PENDING 单（PARENT 不会成为审批人，天然空） */
    public List<Map<String, Object>> todoList() {
        var user = AuthUtil.current();
        Long uid = user.userId();
        List<OaForm> all = formMapper.selectList(new LambdaQueryWrapper<OaForm>()
                .eq(OaForm::getStatus, OaForm.PENDING));
        List<OaForm> mine = all.stream()
                .filter(f -> uid.equals(approverId(f.getFormType(), f.getCurrentLevel())))
                .collect(Collectors.toList());
        return toRows(mine);
    }

    /** 管理端全量 */
    public List<Map<String, Object>> allList(String formType, String status) {
        return toRows(formMapper.selectList(new LambdaQueryWrapper<OaForm>()
                .eq(formType != null && !formType.isBlank(), OaForm::getFormType, formType)
                .eq(status != null && !status.isBlank(), OaForm::getStatus, status)
                .orderByDesc(OaForm::getId)));
    }

    /** 单据详情：单+明细+流转日志（申请人与审批人可见；教师只能看自己的+待我审的） */
    public Map<String, Object> detail(Long id) {
        var user = AuthUtil.current();
        OaForm form = requireForm(id);
        if (!"ADMIN".equals(user.role())) {
            boolean applicant = form.getApplicantId().equals(user.userId());
            boolean approver = form.getStatus().equals(OaForm.PENDING)
                    && user.userId().equals(approverId(form.getFormType(), form.getCurrentLevel()));
            if (!applicant && !approver) {
                throw new BizException(403, "仅申请人与当前审批人可查看该单据");
            }
        }
        List<OaFlowLog> logs = logMapper.selectList(new LambdaQueryWrapper<OaFlowLog>()
                .eq(OaFlowLog::getFormId, id).orderByAsc(OaFlowLog::getId));
        Map<Long, String> names = userNames(form, logs);
        Map<String, Object> out = rowOf(form, names);
        // 明细原文（SEAL={reason,useDate}，GOODS=[{goodsId,name,qty,unit,location}]），前端按类型 parse
        out.put("detail", form.getDetail());
        out.put("levels", levels(form.getFormType()));
        out.put("logs", logs.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("action", l.getAction());
            m.put("nodeName", l.getNodeName());
            m.put("operatorName", names.getOrDefault(l.getOperatorId(), ""));
            m.put("note", l.getNote() == null ? "" : l.getNote());
            m.put("createTime", l.getCreateTime());
            return m;
        }).toList());
        return out;
    }

    // ---- 审批动作 ----

    @Data
    public static class HandleReq {
        private String action; // AGREE / REJECT / REVOKE
        private String note;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(Long formId, HandleReq req) {
        var user = AuthUtil.current();
        String action = req.getAction();
        OaForm form = requireForm(formId);
        boolean admin = "ADMIN".equals(user.role());
        if (OaFlowLog.REVOKE.equals(action)) {
            if (!form.getApplicantId().equals(user.userId())) {
                throw new BizException(403, "仅申请人本人可撤回");
            }
            if (!form.getStatus().equals(OaForm.PENDING)) {
                throw new BizException(400, "单据已流转结束，无法撤回");
            }
            writeLog(form, OaFlowLog.REVOKE, user.userId(), req.getNote());
            finish(form, OaForm.REVOKED);
            return;
        }
        if (!form.getStatus().equals(OaForm.PENDING)) {
            throw new BizException(400, "单据已流转结束");
        }
        Long approver = approverId(form.getFormType(), form.getCurrentLevel());
        boolean currentApprover = user.userId().equals(approver);
        if (!admin && !currentApprover) {
            throw new BizException(403, "当前节点由「" + nodeName(form.getCurrentLevel()) + "」审批人处理");
        }
        if (OaFlowLog.REJECT.equals(action)) {
            writeLog(form, OaFlowLog.REJECT, user.userId(), req.getNote());
            finish(form, OaForm.REJECTED);
            return;
        }
        if (!OaFlowLog.AGREE.equals(action)) {
            throw new BizException(400, "未知审批动作");
        }
        writeLog(form, OaFlowLog.AGREE, user.userId(), req.getNote());
        if (form.getCurrentLevel() >= levels(form.getFormType())) {
            finish(form, OaForm.APPROVED);
            if (OaForm.TYPE_GOODS.equals(form.getFormType())) {
                issueGoods(form, user.userId()); // 扣库存+出库流水；库存不足抛错整体回滚（单据停在当前级）
            }
        } else {
            form.setCurrentLevel(form.getCurrentLevel() + 1);
            formMapper.updateById(form);
        }
    }

    /** 末级通过后出库：逐行原子扣减+流水（「谁/何时/哪里/拿走了什么」全落在 OUT 行） */
    private void issueGoods(OaForm form, Long operatorId) {
        for (Map<String, Object> line : parseDetail(form.getDetail())) {
            Long goodsId = ((Number) line.get("goodsId")).longValue();
            int qty = ((Number) line.get("qty")).intValue();
            if (goodsMapper.deductStock(goodsId, qty) == 0) {
                Goods g = goodsMapper.selectById(goodsId);
                throw new BizException(409, "「" + line.get("name") + "」库存不足（剩 " + (g == null ? 0 : g.getStock()) + "），请先补库或调减数量后重审");
            }
            GoodsFlow flow = new GoodsFlow();
            flow.setGoodsId(goodsId);
            flow.setGoodsName((String) line.get("name"));
            flow.setQty(qty);
            flow.setDirection(GoodsFlow.OUT);
            flow.setLocation((String) line.get("location"));
            flow.setFormId(form.getId());
            flow.setApplicantId(form.getApplicantId());
            flow.setOperatorId(operatorId);
            goodsFlowMapper.insert(flow);
        }
    }

    // ---- 工具 ----

    private OaForm requireForm(Long id) {
        OaForm form = formMapper.selectById(id);
        if (form == null) {
            throw new BizException(404, "单据不存在");
        }
        return form;
    }

    public static String nodeName(int level) {
        return switch (level) {
            case 1 -> "一级审批";
            case 2 -> "二级审批";
            case 3 -> "三级审批";
            default -> "提交";
        };
    }

    private void writeLog(OaForm form, String action, Long operatorId, String note) {
        OaFlowLog log = new OaFlowLog();
        log.setFormId(form.getId());
        log.setAction(action);
        log.setLevel(OaFlowLog.SUBMIT.equals(action) ? 0 : form.getCurrentLevel());
        log.setNodeName(OaFlowLog.SUBMIT.equals(action) ? NODE_SUBMIT : nodeName(form.getCurrentLevel()));
        log.setOperatorId(operatorId);
        log.setNote(note);
        logMapper.insert(log);
    }

    private void finish(OaForm form, String status) {
        form.setStatus(status);
        form.setFinishTime(java.time.LocalDateTime.now());
        formMapper.updateById(form);
    }

    private List<Map<String, Object>> toRows(List<OaForm> forms) {
        Map<Long, String> names = forms.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(forms.stream().map(OaForm::getApplicantId).distinct().toList())
                        .stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        return forms.stream().map(f -> rowOf(f, names)).collect(Collectors.toList());
    }

    private Map<String, Object> rowOf(OaForm f, Map<Long, String> names) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("formType", f.getFormType());
        m.put("typeName", typeName(f.getFormType()));
        m.put("title", f.getTitle());
        m.put("applicantName", names.getOrDefault(f.getApplicantId(), ""));
        m.put("status", f.getStatus());
        m.put("currentLevel", f.getCurrentLevel());
        m.put("nodeName", f.getStatus().equals(OaForm.PENDING) ? nodeName(f.getCurrentLevel()) : "");
        m.put("createTime", f.getCreateTime());
        return m;
    }

    private Map<Long, String> userNames(OaForm form, List<OaFlowLog> logs) {
        List<Long> ids = new ArrayList<>(logs.stream().map(OaFlowLog::getOperatorId).distinct().toList());
        ids.add(form.getApplicantId());
        return userMapper.selectBatchIds(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
    }

    private String toJson(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(500, "明细序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDetail(String detail) {
        try {
            Object o = JSON.readValue(detail, Object.class);
            return o instanceof List ? (List<Map<String, Object>>) o : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
