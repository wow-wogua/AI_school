package com.aischool.server.security;

import com.aischool.server.mapper.AuditLogMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.security.SecurityProperties;

/** 审计过滤器注册：挂在安全链之后（能取到当前用户）、业务控制器之前 */
@Configuration
public class AuditConfig {

    @Bean
    public FilterRegistrationBean<AuditFilter> auditFilter(AuditLogMapper auditLogMapper) {
        FilterRegistrationBean<AuditFilter> reg = new FilterRegistrationBean<>(new AuditFilter(auditLogMapper));
        reg.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 20);
        reg.addUrlPatterns("/api/*");
        reg.setName("auditFilter");
        return reg;
    }
}
