package cn.zjw.common.utils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

import cn.zjw.config.OssProperties;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OssUtil {

    @Autowired
    private OssProperties ossProperties;

    public String upload(MultipartFile file) {
        // 1.生成唯一文件名，防止覆盖
        // 格式：images/年月日/UUID.扩展名
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = "images/" + LocalDate.now() + "/" + UUID.randomUUID() + ext;
        // 2.创建OSS客户端（用完要关闭）
        OSS ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
        try {
            // 上传
            ossClient.putObject(ossProperties.getBucketName(), objectName, file.getInputStream());
            log.info("上传图片成功......");
            // 拼接返回URL
            return ossProperties.getUrlPrefix() + objectName;
        } catch (IOException e) {
            throw new RuntimeException("OSS上传失败", e);
        } finally {
            ossClient.shutdown(); // 必须关闭，否则连接泄漏
            log.info("释放OSS客户端......");
        }
    }
}
