package cn.zjw.common.result;
import cn.zjw.common.result.ResultCode;
import lombok.Data;
import lombok.Builder;

/**
 * 统一返回结果
 */
@Data
@Builder
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS_CODE, ResultCode.SUCCESS_MESSAGE);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS_CODE, ResultCode.SUCCESS_MESSAGE, data);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.ERROR_CODE, message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message);
    }
}
