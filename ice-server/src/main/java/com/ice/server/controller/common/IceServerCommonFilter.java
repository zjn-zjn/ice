package com.ice.server.controller.common;

import com.ice.server.nio.IceNioServerInit;
import com.ice.server.nio.ha.IceNioServerHa;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * @author waitmoon
 */
@Slf4j
@Component
public class IceServerCommonFilter implements Filter {

    @Autowired(required = false)
    private IceNioServerHa serverHa;

    public static final String HTTP_PREFIX = "http://";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if (!IceNioServerInit.ready) {
            servletResponse.getWriter().print("server not ready...please retry later");
            return;
        }
        // leader
        if (serverHa != null && !serverHa.isLeader()) {
            try {
                String leaderWebAddress = serverHa.getLeaderWebAddress();
                log.info("redirect to leader, {}", leaderWebAddress);

                HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                String queryString = getQueryString(httpServletRequest);

                String requestFullUrl = getProtocol(httpServletRequest) +
                        leaderWebAddress +
                        getUri(httpServletRequest) +
                        queryString;

                ((HttpServletResponse) servletResponse).sendRedirect(requestFullUrl);
            } catch (Exception e) {
                log.error("not leader response error", e);
            }
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private String getUri(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return Objects.equals("/", requestURI) ? "" : requestURI;
    }

    private String getProtocol(HttpServletRequest request) {
        return request.getScheme() + "://";
    }

    private String getQueryString(HttpServletRequest request) {
        String queryString = request.getQueryString();
        queryString = StringUtil.isNullOrEmpty(queryString) ? "" : "?" + queryString;
        return queryString;
    }

}