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
public class  ReviewRatingRefreshMessage implements Serializable {
    /**
     * 餐厅ID
     */
    private Long restaurantId;


}
