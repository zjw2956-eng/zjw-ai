/*
 * 统一返回结果状态码枚举
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
package cn.zjw.common.result;

import lombok.Getter;

/**
 * 统一返回结果状态码枚举
 */
@Getter
public enum ResultCode {

    // --- 成功状态码 --- (2xx)
    SUCCESS(200, "操作成功~"),
    CREATED(201, "资源创建成功"),

    // --- 客户端错误状态码 --- (4xx)
    BAD_REQUEST(400, "错误的请求"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "权限不足，禁止访问"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    REQUEST_TIMEOUT(408, "请求超时"),
    CONFLICT(409, "请求发生冲突"), // 例如，资源已存在
    VALIDATION_ERROR(422, "参数校验失败"), // Unprocessable Entity
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // --- 服务端错误状态码 --- (5xx)
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),
    GATEWAY_TIMEOUT(504, "网关超时");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // 可以添加根据code查找枚举的方法，如果需要的话
    public static ResultCode fromCode(int code) {
        for (ResultCode rc : ResultCode.values()) {
            if (rc.getCode() == code) {
                return rc;
            }
        }
        // 可以返回一个默认错误码或抛出异常
        return INTERNAL_SERVER_ERROR; // 或者 throw new IllegalArgumentException("Invalid ResultCode: " + code);
    }
}
