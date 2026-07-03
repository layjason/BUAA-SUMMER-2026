package io.github.layjason.mayoistar.api.common;

import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.service.media.MediaAccessDescriptor;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private final MediaAccessService mediaAccessService;

    /**
     * 通过签名 URL 获取文件内容。
     *
     * <p>前置条件：mediaId 对应有效的 MediaFile 记录，请求携带服务端签发的 v、policy、scope、sig。
     *
     * <p>后置条件：签名和权限校验通过后，通过 FileStorageService 流式返回文件内容，HTTP 状态码固定为 200。
     *
     * <p>行为：
     * <ul>
     *   <li>签名缺失或被篡改 → 403</li>
     *   <li>私有资源未认证 → 401</li>
     *   <li>私有资源无权限 → 403</li>
     *   <li>mediaId 不存在或已软删除 → 404</li>
     *   <li>文件存在 → 流式输出文件内容（Content-Type 正确设置）</li>
     *   <li>文件在存储中不存在 → 404</li>
     * </ul>
     *
     * @param mediaId        媒体文件唯一标识
     * @param accessVersion  访问版本
     * @param policy         访问策略
     * @param scope          访问作用域
     * @param sig            URL 签名
     * @param authentication 当前认证信息，可为空
     * @return 文件流式响应
     */
    @GetMapping("/{mediaId}")
    public ResponseEntity<InputStreamResource> getMediaFile(
            @PathVariable UUID mediaId,
            @RequestParam("v") long accessVersion,
            @RequestParam MediaAccessPolicy policy,
            @RequestParam(defaultValue = "") String scope,
            @RequestParam(required = false) String sig,
            Authentication authentication) {
        InputStream inputStream =
                mediaAccessService.openSignedContent(mediaId, accessVersion, policy, scope, sig, authentication);
        MediaAccessDescriptor descriptor = mediaAccessService.loadDescriptor(mediaId);
        InputStreamResource resource = new InputStreamResource(inputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(descriptor.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(sanitizeHeaderValue(descriptor.fileName()))
                .build());
        headers.setContentLength(descriptor.sizeBytes());

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    private static String sanitizeHeaderValue(String input) {
        return input.replace('\r', '_').replace('\n', '_');
    }
}
