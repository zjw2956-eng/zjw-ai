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
        // 实现注册逻辑
        LambdaQueryWrapper<User> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername,dto.getUsername());
        User existUser=userMapper.selectOne(wrapper);
        if(existUser!=null){
            throw new IllegalArgumentException("用户名已存在");
        }
        LambdaQueryWrapper<User> wrapper2=new LambdaQueryWrapper<>();
        wrapper2.eq(User::getPhone,dto.getPhone());
        User existPhoneUser=userMapper.selectOne(wrapper2);
        if(existPhoneUser!=null){
            throw new IllegalArgumentException("手机号已存在");
        }
        // 密码加密
        String encryptedPassword=BCrypt.hashpw(dto.getPassword(),BCrypt.gensalt());

        User user=new User();
        user.setUsername(dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setPassword(encryptedPassword);
        user.setNickname(dto.getNickname());
        userMapper.insert(user);
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
