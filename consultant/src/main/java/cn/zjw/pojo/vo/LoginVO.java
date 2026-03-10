package cn.zjw.pojo.vo;

import lombok.Data;

/**
 * 登录VO
 */
@Data   
public class LoginVO {

    private String token;
    private UserVO user; 
}