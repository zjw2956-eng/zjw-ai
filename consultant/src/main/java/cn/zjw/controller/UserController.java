package cn.zjw.controller;

import cn.zjw.common.result.Result;
import cn.zjw.pojo.dto.UserLoginDTO;
import cn.zjw.pojo.dto.UserRegisterDTO;
import cn.zjw.pojo.vo.UserVO;
import cn.zjw.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.common.context.UserContext;

/**
 * 用户Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        // 实现注册逻辑
        log.info("用户注册:{}",dto);
        userService.register(dto);
        return Result.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody UserLoginDTO dto) {
        //实现登录逻辑
        log.info("用户登录:{}",dto);
        String token=userService.login(dto);
        return Result.success(token);
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public Result<UserVO> getUserInfo() {
        // 从token中获取userId，查询用户信息
        log.info("获取用户信息");
        Long userId=UserContext.getCurrentUserId();
        UserVO userVO=userService.getUserInfo(userId);
        return Result.success(userVO);
    }
}
