package cn.zjw.service.impl;

import cn.zjw.mapper.UserMapper;
import cn.zjw.pojo.dto.UserLoginDTO;
import cn.zjw.pojo.dto.UserRegisterDTO;
import cn.zjw.pojo.vo.UserVO;
import cn.zjw.service.UserService;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.common.utils.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import cn.zjw.common.constant.Constants;
import cn.zjw.pojo.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.bcrypt.BCrypt;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户Service实现
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

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
        // 实现登录逻辑，返回JWT token
        LambdaQueryWrapper<User> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername,dto.getUsername());
        User user=userMapper.selectOne(wrapper);
        if(user==null){
            throw new IllegalArgumentException("用户名不存在");
        }
        if(!BCrypt.checkpw(dto.getPassword(),user.getPassword())){
            throw new IllegalArgumentException("密码错误");
        }
        String token=jwtUtil.generateToken(user.getId());
        stringRedisTemplate.opsForValue().set(Constants.REDIS_USER_TOKEN + user.getId(), token, Constants.REDIS_EXPIRE_TIME,TimeUnit.SECONDS);
        return token;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        // 实现获取用户信息逻辑
        User user=userMapper.selectById(userId);
        if(user==null){
            throw new IllegalArgumentException("用户不存在");
        }
        UserVO userVO=new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setPhone(user.getPhone());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(user.getAvatar());
        userVO.setGender(user.getGender());
        userVO.setBirthday(user.getBirthday());
        return userVO;
    }
}
