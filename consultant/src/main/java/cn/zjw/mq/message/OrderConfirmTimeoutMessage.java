package cn.zjw.mq.message;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单确认超时消息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderConfirmTimeoutMessage implements Serializable {
    /**
     * 订单号
     */
    private String orderNo;

    @Override
    public String toString() {
        return "OrderConfirmTimeoutMessage{orderNo='" + orderNo + "'}";
    }

}
