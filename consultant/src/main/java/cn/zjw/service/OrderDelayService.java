package cn.zjw.service;

import java.time.LocalDateTime;
import java.util.Set;

public interface OrderDelayService {

    /**
     * 
     * @param orderNo
     * @param reservationTime
     */
    void enqueueNoShowCancel(String orderNo, LocalDateTime reservationTime);

    Set<String> pollDueOrders(long nowEpochMillis, int limit);

    // 返回锁值（拿到锁返回uuid，拿不到返回null）
    String tryLock(String orderNo);

    // 传回锁值做安全解锁
    void unlock(String orderNo,String lockValue);

    void removeFromDelayQueue(String orderNo);
}