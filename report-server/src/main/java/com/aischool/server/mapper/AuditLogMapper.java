package com.aischool.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aischool.server.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
