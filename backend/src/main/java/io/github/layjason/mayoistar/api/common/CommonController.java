package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/common")
public class CommonController {

    private final MediaFileRepository mediaFileRepository;
    private final Path uploadRoot;

    public CommonController(
            MediaFileRepository mediaFileRepository, @Value("${mayoistar.upload.root:uploads}") String uploadRoot) {
        this.mediaFileRepository = mediaFileRepository;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    /**
     * 根据媒体文件标识获取文件原始内容。
     *
     * <p>前置条件：mediaId 对应一条有效的 media_files 记录。
     *
     * <p>后置条件：返回文件二进制流，Content-Type 设置为文件原始 MIME 类型。
     * 若 mediaId 不存在或文件在磁盘上丢失，返回 404。
     *
     * <p>不变量：不修改数据库和文件系统。
     *
     * @param mediaId 媒体文件标识
     * @return 文件二进制流响应，或 404
     */
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<Resource> getMediaFile(@PathVariable String mediaId) {
        String safeId = mediaId.replace("\n", "\\n").replace("\r", "\\r");
        MediaFile mediaFile = mediaFileRepository.findById(mediaId).orElse(null);
        if (mediaFile == null) {
            log.warn("媒体文件不存在: mediaId={}", safeId);
            return ResponseEntity.notFound().build();
        }

        Path filePath = uploadRoot.resolve(mediaFile.getStoragePath()).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            log.warn("媒体文件路径越界: mediaId={}, storagePath={}", safeId, mediaFile.getStoragePath());
            return ResponseEntity.notFound().build();
        }

        if (!Files.exists(filePath)) {
            log.warn("媒体文件在磁盘上不存在: mediaId={}, path={}", safeId, filePath);
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new InputStreamResource(Files.newInputStream(filePath));
            MediaType contentType = MediaType.parseMediaType(mediaFile.getContentType());
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(Files.size(filePath))
                    .body(resource);
        } catch (Exception e) {
            log.error("读取媒体文件失败: mediaId={}", safeId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
