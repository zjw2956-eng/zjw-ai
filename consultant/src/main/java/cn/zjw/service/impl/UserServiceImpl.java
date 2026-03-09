package cn.zjw.service.impl;

import cn.zjw.mapper.UserMapper;
import cn.zjw.pojo.dto.UserLoginDTO;
import cn.zjw.pojo.dto.UserRegisterDTO;
import cn.zjw.pojo.vo.UserVO;
import cn.zjw.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void register(UserRegisterDTO dto) {
        // TODO: 实现注册逻辑
    }

    @Override
    public String login(UserLoginDTO dto) {
        // TODO: 实现登录逻辑，返回JWT token
        return null;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        // TODO: 实现获取用户信息逻辑
        return null;
    }
}
