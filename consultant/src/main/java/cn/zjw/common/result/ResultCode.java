package cn.zjw.common.result;

public class ResultCode {
    
    public static final Integer SUCCESS_CODE = 200;
    public static final String SUCCESS_MESSAGE = "操作成功";
    public static final Integer ERROR_CODE = 500;
    public static final String ERROR_MESSAGE = "服务器内部错误";
    public static final Integer PARAM_ERROR_CODE = 400;
    public static final String PARAM_ERROR_MESSAGE = "参数校验失败";    
    public static final Integer UNAUTHORIZED_CODE = 401;
    public static final String UNAUTHORIZED_MESSAGE = "未授权/登录过期/登录失败";
    public static final Integer FORBIDDEN_CODE = 403;
    public static final String FORBIDDEN_MESSAGE = "拒绝访问";
    public static final Integer NOT_FOUND_CODE = 404;
    public static final String NOT_FOUND_MESSAGE = "资源不存在";
}
