package cn.zjw.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.zjw.service.HotRankService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HotRankScheduler {

    @Autowired
    private HotRankService hotRankService;

    @Scheduled(cron = "0 0 0 1 * ?")
    public void refresh() {
        hotRankService.refreshHotRestaurantRank();
    }
}
