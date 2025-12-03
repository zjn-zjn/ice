package com.ice.server.controller.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.io.IOException;

/**
 * @author waitmoon
 * 简化版Filter，移除了HA相关逻辑
 */
@Slf4j
@Component
public class IceServerCommonFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // 直接放行，不再需要HA检查
        filterChain.doFilter(servletRequest, servletResponse);
    }
}