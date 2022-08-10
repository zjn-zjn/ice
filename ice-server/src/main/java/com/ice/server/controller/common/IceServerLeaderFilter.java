package com.ice.server.controller.common;

import com.ice.server.nio.ha.IceNioServerHa;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author waitmoon
 */
@Slf4j
@Component
public class IceServerLeaderFilter implements Filter {

    @Autowired(required = false)
    private IceNioServerHa serverHa;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (serverHa != null && !serverHa.isLeader()) {
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.setContentType("text/html; charset=utf-8");
            try (PrintWriter writer = servletResponse.getWriter()) {
                writer.print("current leader page: " + serverHa.getLeaderWebAddress());
            } catch (Exception e) {
                log.error("not leader response error", e);
            }
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}