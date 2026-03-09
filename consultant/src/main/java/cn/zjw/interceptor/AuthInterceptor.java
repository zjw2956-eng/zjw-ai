package cn.zjw.interceptor;

import cn.zjw.common.context.UserContext;
import cn.zjw.common.exception.UnauthorizedException;
import cn.zjw.common.utils.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录校验拦截器
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");

        // TODO: 实现JWT校验逻辑
        // 1. 解析token获取userId
        // 2. 将userId存入UserContext
        // 3. 校验失败抛出UnauthorizedException

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
