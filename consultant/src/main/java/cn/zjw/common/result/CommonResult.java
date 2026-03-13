package cn.zjw.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果
 *  * success 方法用于成功的返回结果，默认状态码为 200。
 * error 方法用于错误的返回结果，允许自定义状态码和错误消息。
 * <p>
 * 常见http状态码:
 * 200：请求成功
 * 201 （已创建） - 请求成功并且服务器创建了新的资源。
 * 304 （未修改） - 自从上次请求后，请求的网页未修改过。服务器返回此响应时，不会返回网页内容。
 * 400：Bad Request 当前请求无法被服务器理解。请求和服务器对接不上。发送请求时出现的问题。
 * 401: unauthorized（未授权的、非法的）表示发送的请求需要有通过 HTTP 认证的认证信息
 * 403: （禁止） 服务器拒绝请求。服务器不接受请求。（可能没有权限）
 * 404：找不到页面，路径有问题。
 * 409："冲突"（Conflict）。这个状态码通常用于表示请求与服务器的当前状态之间存在冲突。资源\版本\并发\业务逻辑冲突
 * 500：（服务器内部错误） 服务器遇到错误，无法完成请求。服务器中的错误即请求成功后服务器运行出现错误。java代码写的有问题。
 * 502：（错误网关） 服务器作为网关或代理，从上游服务器收到无效响应。
 * 503 ：（服务不可用） 服务器目前无法使用（由于超载或停机维护）。通常，这只是暂时状态。
 * 504：（网关超时）服务器作为网关或代理，但是没有及时从上游服务器收到请求。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommonResult<T> {
    private int code;
    private String msg;
    private T data;



    /**
     * 成功结果，使用默认成功消息。
     * @param data 成功数据
     * @return CommonResult实例
     */
    public static <T> CommonResult<T> success(T data) {
        return new CommonResult<>(ResultCode.SUCCESS.getCode(),ResultCode.SUCCESS.getMessage(),data);
    }

    /**
     * 无数据的成功结果，使用默认成功消息。
     * @return CommonResult实例
     */
    public static CommonResult<Void> success() {
        return new CommonResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功结果，使用 ResultCode 定义的 code 和 message。
     * @param resultCode 成功状态码枚举
     * @return CommonResult实例
     */
    public static <T> CommonResult<T> success(ResultCode resultCode) {
        return new CommonResult<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 成功结果，使用 ResultCode 定义的 code 和 message，并携带数据。
     * @param resultCode 成功状态码枚举
     * @param data 成功数据
     * @return CommonResult实例
     */
    public static <T> CommonResult<T> success(ResultCode resultCode, T data) {
        return new CommonResult<>(resultCode.getCode(), resultCode.getMessage(), data);
    }

    /**
     * 
     * @param <T>
     * @param resultCode
     * @param msg
     * @param data
     * @return
     */
    public static <T> CommonResult<T> success(ResultCode resultCode, String msg, T data ) {
        return new CommonResult<>(resultCode.getCode(), resultCode.getMessage(), data);
    }

     /**
     * 错误结果，使用 ResultCode 定义的 code 和 message。
     * @param resultCode 错误状态码枚举
     * @return CommonResult实例
     */
     public static <T> CommonResult<T> error(ResultCode resultCode) {
        return new CommonResult<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 错误结果，使用 ResultCode 定义的 code，但使用自定义消息。
     * @param resultCode 错误状态码枚举
     * @param message 自定义错误消息
     * @return CommonResult实例
     */
    public static <T> CommonResult<T> error(ResultCode resultCode, String message) {
        return new CommonResult<>(resultCode.getCode(), message, null);
    }

    /**
     * 错误结果，使用 ResultCode 定义的 code 和自定义消息，并携带数据。
     * 用于 HTTP 500 等错误场景下仍需要返回 data 的情况。
     *
     * @param resultCode 错误状态码枚举
     * @param message 自定义错误消息
     * @param data 错误时的附加数据（如完整 Redis 对象）
     * @return CommonResult实例
     */
    public static <T> CommonResult<T> error(ResultCode resultCode, String message, T data) {
        return new CommonResult<>(resultCode.getCode(), message, data);
    }

    // --- 实例方法 ---

    /**
     * 判断当前结果是否为成功状态。
     * @return 如果 code 等于 ResultCode.SUCCESS.getCode()，则返回 true，否则返回 false。
     */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
