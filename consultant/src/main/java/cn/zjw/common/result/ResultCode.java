package cn.zjw.common.result;

public class ResultCode {
    
    private static final Integer SUCCESS_CODE = 200;
    private static final String SUCCESS_MESSAGE = "操作成功";
    private static final Integer ERROR_CODE = 500;
    private static final String ERROR_MESSAGE = "服务器内部错误";
    private static final Integer PARAM_ERROR_CODE = 400;
    private static final String PARAM_ERROR_MESSAGE = "参数校验失败";
    private static final Integer UNAUTHORIZED_CODE = 401;
    private static final String UNAUTHORIZED_MESSAGE = "未授权/登录过期/登录失败";
    private static final Integer FORBIDDEN_CODE = 403;
    private static final String FORBIDDEN_MESSAGE = "拒绝访问";
    private static final Integer NOT_FOUND_CODE = 404;
    private static final String NOT_FOUND_MESSAGE = "资源不存在";
}
