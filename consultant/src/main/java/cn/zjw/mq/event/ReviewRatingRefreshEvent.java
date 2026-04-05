package cn.zjw.mq.event;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

/**
 * 通过评价事件监听
 */
@Getter
public class ReviewRatingRefreshEvent extends ApplicationEvent {

    /**
     * 餐厅ID
     */
    private final Long restaurantId;

    public ReviewRatingRefreshEvent(Object source, Long restaurantId) {
            super(source);
            this.restaurantId = restaurantId;
        }

}
