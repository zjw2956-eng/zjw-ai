package cn.zjw.service.impl;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import cn.zjw.common.utils.JwtUtil;
import cn.zjw.mapper.UserMapper;
import cn.zjw.pojo.dto.UserLoginDTO;
import cn.zjw.pojo.dto.UserRegisterDTO;
import cn.zjw.pojo.entity.User;
import cn.zjw.pojo.vo.UserVO;
import cn.zjw.service.UserService;
import lombok.extern.slf4j.Slf4j;

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

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(UserRegisterDTO dto) {
        String lockKey = Constants.REDIS_LOCK_USER_REGISTER_KEY + dto.getUsername();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(
                    Constants.LOCK_GET_TIME,
                    Constants.LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS); // 抢锁：不等待，拿不到立即返回 false，持有10秒
            if (!locked) {
                throw new RuntimeException("获取锁失败......");
            }
            String encryptedPassword = BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt());
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(User::getUsername, dto.getUsername());
            if (userMapper.selectCount(userWrapper) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "用户名重复");
            }
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, dto.getPhone());
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "手机号码重复");
            }
            User user = new User();
            user.setUsername(dto.getUsername());
            user.setPhone(dto.getPhone());
            user.setPassword(encryptedPassword);
            user.setNickname(dto.getNickname());
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 解析是哪个字段冲突
            String msg = e.getMessage();
            if (msg.contains("uk_username")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已存在");
            } else if (msg.contains("uk_phone")) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "手机号已注册");
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "注册失败，请重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("用户注册被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String login(UserLoginDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名不存在");
        }
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码错误");
        }
        String token = jwtUtil.generateToken(user.getId());
        stringRedisTemplate.opsForValue().set(
                Constants.REDIS_USER_TOKEN + user.getId(),
                token,
                Constants.REDIS_EXPIRE_TIME,
                TimeUnit.SECONDS);
        return token;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        // String json =
        // stringRedisTemplate.opsForValue().get(Constants.REDIS_USER_INFO_KEY+userId);
        // if (json != null) {
        // return JSONUtil.toBean(json, UserVO.class);
        // }
        UserVO result = cacheClient.queryWithMutex(
                Constants.REDIS_USER_INFO_KEY,
                Constants.REDIS_LOCK_USER_KEY,
                userId,
                UserVO.class,
                this::buildUserVO,
                Constants.REDIS_USER_INFO_EXPIRE_TIME,
                Constants.REDIS_EMPTY_KEY_EXPIRE_TIME,
                "user");
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return result;
    }

    private UserVO buildUserVO(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
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
