package cn.zjw.handler;

import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.exception.UnauthorizedException;
import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public CommonResult<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage());
        return CommonResult.error(ResultCode.fromCode(e.getCode()), e.getMessage());
    }

    /**
     * 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return CommonResult.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public CommonResult<Void> handleUnauthorizedException(UnauthorizedException e) {
        return CommonResult.error(ResultCode.UNAUTHORIZED, "未登录或登录已过期");
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后再试");
    }
}
