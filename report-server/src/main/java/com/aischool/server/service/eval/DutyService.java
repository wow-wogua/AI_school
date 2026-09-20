package com.aischool.server.service.eval;

import com.aischool.server.common.BizException;
import com.aischool.server.entity.DutySchedule;
import com.aischool.server.entity.SysConfig;
import com.aischool.server.mapper.DutyScheduleMapper;
import com.aischool.server.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 值班排班（批6 漏项C1）：t_sys_config.duty_check 开（管理端配置）后，
 * 日常评价仅「当日值班教师」可操作（口径=操作发生日，ADMIN/LEADER 不受限——监督角色不参与日常录入）。
 * 开关默认关：学校配好排班再开，避免空排班把全员锁死。
 */
@Service
@RequiredArgsConstructor
public class DutyService {

    public static final String CFG_DUTY_CHECK = "duty_check";

    private final SysConfigMapper sysConfigMapper;
    private final DutyScheduleMapper dutyScheduleMapper;

    public boolean isEnabled() {
        SysConfig cfg = sysConfigMapper.selectById(CFG_DUTY_CHECK);
        return cfg != null && "1".equals(cfg.getCfgValue());
    }

    public void setEnabled(boolean enabled) {
        SysConfig cfg = new SysConfig();
        cfg.setCfgKey(CFG_DUTY_CHECK);
        cfg.setCfgValue(enabled ? "1" : "0");
        sysConfigMapper.updateById(cfg); // 行由 V18 种子保证存在
    }

    public boolean isOnDuty(Long teacherId, LocalDate date) {
        return dutyScheduleMapper.selectCount(new LambdaQueryWrapper<DutySchedule>()
                .eq(DutySchedule::getDutyDate, date)
                .eq(DutySchedule::getTeacherId, teacherId)) > 0;
    }

    /** 评价写入口的守卫：开关开着且非管理角色时，必须是当日值班教师 */
    public void checkCanEvaluate(String role, Long userId) {
        if (!isEnabled() || "ADMIN".equals(role) || "LEADER".equals(role)) {
            return;
        }
        if (!isOnDuty(userId, LocalDate.now())) {
            throw new BizException(403, "今日不在值班名单，日常评价仅当日值班老师可操作（如需调整请联系管理员）");
        }
    }
}
