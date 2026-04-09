package cn.zjw.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cn.zjw.ai.service.AiHelperService;
@SpringBootTest
class AiHelperServiceTest {

    @Autowired
    private AiHelperService aiHelperService;

//    @Test
//    void chatForReport() {
//        String userMessage = "你好，我是zjw，学编程两年半，请帮我制定学习报告";
//        AiHelperService.Report report = aiHelperService.chatForReport(userMessage);
//        System.out.println(report);
//    }
//    @Test
//    void chat() {
//        String flux = aiHelperService.chat("你好,我叫大哥");
//        System.out.println(flux);
//    }

//    @Test
//    void chatWithMemory() {
//        String result = aiHelperService.chat("你好，我是zjw");
//        System.out.println(result);
//        result = aiHelperService.chat("你好，我是谁来着？");
//        System.out.println(result);
//    }

//    @Test
//    void chatWithRag() {
//        String result = aiHelperService.chatWithRag("成都小众美食有哪些，不用详解，就说名字");
//        System.out.println(result);
//
//    }
    // @Test
    // void chatWithRag() {
    //     Result<String> result = aiHelperService.chatWithRag2("成都小众美食有哪些，不用详解，就说名字");
    //     System.out.println(result.sources());
    //     System.out.println(result.content());
    // }

//     @Test
//     void chatWithTool() {
//         String result = aiHelperService.chat(123,"我想预订餐厅");
//         System.out.println(result);
//     }


}