package cn.zjw.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.common.utils.OssUtil;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequestMapping("/api/upload")
public class FileUploadController {
    
    @Autowired
    private OssUtil ossUtil;
    
    @PostMapping
    public CommonResult<String> upload(@RequestParam("file") MultipartFile file){
        //基本校验，文件不能为空，只允许图片类型
        if(file.isEmpty()){
            return CommonResult.error(ResultCode.BAD_REQUEST,"文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return CommonResult.error(ResultCode.BAD_REQUEST,"只支持图片格式");
        }
        String url = ossUtil.upload(file);
        return CommonResult.success(url);
    }
}
