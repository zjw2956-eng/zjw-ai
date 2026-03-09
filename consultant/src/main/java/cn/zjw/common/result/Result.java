package cn.zjw.common.result;
import cn.zjw.common.result.ResultCode;
import lombok.Data;

/**
 * 统一返回结果
 */
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS_CODE, ResultCode.SUCCESS_MESSAGE, null);
    }

    public static <T> Result<T> success(T data) {
        return null;
    }

    public static <T> Result<T> error(String message) {
        return null;
    }

    public static <T> Result<T> error(Integer code, String message) {
        return null;
    }
}
