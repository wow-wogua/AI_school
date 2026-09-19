package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.ConductAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface ConductAccountMapper extends BaseMapper<ConductAccount> {

    /** 首笔以基础分初始化余额，再叠加本笔增减；并发下 uk(student_id,term_id) 兜底不重行 */
    @Insert("INSERT INTO t_conduct_account (student_id, term_id, balance) " +
            "VALUES (#{studentId}, #{termId}, #{base} + #{delta}) " +
            "ON DUPLICATE KEY UPDATE balance = balance + #{delta}")
    int upsertApply(@Param("studentId") Long studentId, @Param("termId") Long termId,
                    @Param("base") BigDecimal base, @Param("delta") BigDecimal delta);
}
