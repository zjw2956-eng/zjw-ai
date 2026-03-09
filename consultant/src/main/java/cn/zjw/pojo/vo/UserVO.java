package cn.zjw.pojo.vo;

import lombok.Data;
import java.time.LocalDate;

/**
 * 用户信息VO
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String phone;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
}
