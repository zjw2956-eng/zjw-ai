package cn.zjw.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.PhoneUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.mapper.ReservationMapper;
import cn.zjw.pojo.entity.Reservation;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FoodReservationTool {

    @Autowired
    private ReservationMapper reservationMapper;
    /**
     * 预约餐厅
     * 当用户明确表达需要预订餐厅的意愿时调用此方法，例如：
     * - "我想预订餐厅"
     * - "帮我订一个位置"
     * - "今晚想在这家餐厅吃饭"
     * - "我要预订包间"
     *
     * 注意：必须引导用户提供完整信息后才能调用，所有参数都必需用户提供，不能模拟任何数据。
     * 如果用户未提供完整信息，应先询问缺失的必填项。
     *
     * @return 预约结果字符串，成功时返回"预约成功"，失败时返回具体错误原因
     */
    @Tool("""
        Reserve a restaurant table.

        Call this method ONLY when the user explicitly expresses the desire
        to make a restaurant reservation:
        - "I want to book a restaurant"
        - "Reserve a table"
        - "Book a private room"
        - "I want to dine at this restaurant tonight"

        IMPORTANT - Do NOT fabricate any data.
        Before calling this method, ensure the user has provided:
        - Reservation name
        - Phone number
        - Restaurant name
        - Reservation date & time in format yyyy-MM-dd HH:mm
        - Number of people
        - Special requests (pass empty string if none)

        If any required information is missing,
        ask the user for it FIRST.

        Returns: "预约成功" on success, or specific error message on failure.
    """)
    public String reserveRestaurant(
            @P("Reservation name") String name,
            @P("Phone number") String phone,
            @P("Restaurant name") String restaurantName,
            @P("Reservation date and time, format: yyyy-MM-dd HH:mm") String reservationTime,
            @P("Number of people") int peopleCount,
            @P("Special requests, pass empty string if none") String specialRequest) {
        try {
            log.info("接收到预订信息: {}, {}, {}, {}, {}, {}", name, phone, restaurantName, reservationTime, peopleCount, specialRequest);
            LocalDateTime reservationTimeObj = LocalDateTime.parse(reservationTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Reservation reservation = new Reservation();
            reservation.setName(name);
            //校验手机号
            if(!PhoneUtil.isMobile(phone)){
                return "手机号格式错误";
            }
            reservation.setPhone(phone);
            reservation.setRestaurantName(restaurantName);
            reservation.setReservationTime(reservationTimeObj);
            reservation.setPeopleCount(peopleCount);
            reservation.setSpecialRequest(specialRequest);
            log.info("预约信息: {}", reservation);
            log.info("预约信息上传中......");
            reservationMapper.insert(reservation);               
            return "预约成功";
        } catch (Exception e) {
            return "预约失败：" + e.getMessage();
        }
    }
    

    /**
     * 查询预订信息
     * 当用户想查询自己已有的餐厅预订记录时调用此方法，例如：
     * - "查询我的预订"
     * - "我预订的餐厅是哪家"
     * - "我的预订信息是什么"
     * - "帮我看看预订详情"
     *
     * 注意：必须提供预订人姓名和手机号两个参数缺一不可。
     *
     * @return 查询结果字符串，包含预订信息或"未找到预订记录"的提示
     */
    @Tool("""
        Query existing restaurant reservation information.

        Call this method when the user wants to check their reservation details:
        - "Check my reservation"
        - "What is my booking"
        - "Show me my reservation details"
        - "When is my reservation"

        REQUIRED parameters (both must be provided):
        - Reservation name
        - Phone number

        Returns: Reservation details if found,
                 or message indicating no reservation was found.
    """)
    public String findReservation(
        @P("Reservation name") String name,
        @P("Phone number") String phone) {
        try {
            log.info("findReservation: {}, {}", name, phone);
            //校验手机号
            if(!PhoneUtil.isMobile(phone)){
                return "手机号格式错误";
            }
            LambdaQueryWrapper<Reservation> wrapper=new LambdaQueryWrapper<>();
            wrapper.eq(Reservation::getName, name)
                    .eq(Reservation::getPhone, phone)
                    .orderByDesc(Reservation::getCreateTime)
                    .last("limit 1");
            Reservation reservation = reservationMapper.selectOne(wrapper);
            if(reservation==null){
                return "没有找到预订信息";
            }
            log.info("findReservation: {}", reservation);
            log.info("查询预订信息中......");
            return "预订信息：" + JSONUtil.toJsonStr(reservation);
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }
}
