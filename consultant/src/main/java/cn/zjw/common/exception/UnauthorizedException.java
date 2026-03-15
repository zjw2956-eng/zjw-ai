package cn.zjw.common.exception;

/**
 * 未授权异常
 */
public class UnauthorizedException extends RuntimeException {
    private Integer code;

    public UnauthorizedException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
