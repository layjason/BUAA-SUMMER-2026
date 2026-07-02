package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体文件访问控制器。
 *
 * <p>类职责：提供通过 mediaId 获取文件内容的公开端点。
 *
 * <p>不变量：所有查询均为只读，不修改媒体文件元数据。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaFileUploadService mediaFileUploadService;

    /**
     * 通过 mediaId 获取文件内容。
     *
     * <p>前置条件：mediaId 对应有效的 MediaFile 记录。
     *
     * <p>后置条件：通过 FileStorageService 流式返回文件内容，HTTP 状态码固定为 200。
     *
     * <p>行为：
     * <ul>
     *   <li>mediaId 不存在 → 404</li>
     *   <li>文件存在 → 流式输出文件内容（Content-Type 正确设置）</li>
     *   <li>文件在存储中不存在 → 404</li>
     * </ul>
     *
     * @param mediaId 媒体文件唯一标识
     * @return 文件流式响应
     */
    @GetMapping("/{mediaId}")
    public ResponseEntity<InputStreamResource> getMediaFile(@PathVariable UUID mediaId) {
        MediaFile mediaFile = mediaFileUploadService.getMediaFile(mediaId);
        InputStream inputStream = mediaFileUploadService.retrieveContent(mediaId);
        InputStreamResource resource = new InputStreamResource(inputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaFile.getContentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(sanitizeHeaderValue(mediaFile.getFileName()))
                .build());
        headers.setContentLength(mediaFile.getSizeBytes());

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    private static String sanitizeHeaderValue(String input) {
        return input.replace('\r', '_').replace('\n', '_');
    }
}
