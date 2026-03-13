package cn.zjw.controller;

import cn.zjw.common.context.UserContext;
import cn.zjw.common.result.CommonResult;
import cn.zjw.pojo.dto.UserLoginDTO;
import cn.zjw.pojo.dto.UserRegisterDTO;
import cn.zjw.pojo.vo.UserVO;
import cn.zjw.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public CommonResult<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        log.info("用户注册:{}", dto);
        userService.register(dto);
        return CommonResult.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public CommonResult<String> login(@Valid @RequestBody UserLoginDTO dto) {
        log.info("用户登录:{}", dto);
        String token = userService.login(dto);
        return CommonResult.success(token);
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public CommonResult<UserVO> getUserInfo() {
        log.info("获取用户信息");
        Long userId = UserContext.getCurrentUserId();
        UserVO userVO = userService.getUserInfo(userId);
        return CommonResult.success(userVO);
    }
}
