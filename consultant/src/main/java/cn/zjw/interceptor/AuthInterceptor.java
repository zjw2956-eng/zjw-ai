package cn.zjw.interceptor;

import cn.zjw.common.context.UserContext;
import cn.zjw.common.exception.UnauthorizedException;
import cn.zjw.common.utils.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.common.constants.Constants;
import org.springframework.data.redis.core.StringRedisTemplate;



/**
 * 登录校验拦截器
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if(auth==null || auth.isBlank()) {
            throw new UnauthorizedException("Token is required");
        }
        if (!auth.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid token format");
        }
        String token = auth.substring("Bearer ".length());
        Long userId=jwtUtil.parseUserId(token);
        String cacheToken=redisTemplate.opsForValue().get(Constants.REDIS_USER_TOKEN + userId);
        if(cacheToken==null || !token.equals(cacheToken)){
            throw new UnauthorizedException("无效的token");
        }
        UserContext.setCurrentUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
