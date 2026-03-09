package cn.zjw.service;

import cn.zjw.pojo.dto.UserLoginDTO;
import cn.zjw.pojo.dto.UserRegisterDTO;
import cn.zjw.pojo.vo.UserVO;

/**
 * 用户Service
 */
public interface UserService {
    /**
     * 用户注册
     */
    void register(UserRegisterDTO dto);

    /**
     * 用户登录
     */
    String login(UserLoginDTO dto);

    /**
     * 获取用户信息
     */
    UserVO getUserInfo(Long userId);
}
