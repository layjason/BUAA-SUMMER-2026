package io.github.layjason.mayoistar.service.ai;

import java.util.List;
import lombok.Data;

/**
 * CLIP 图片分类服务的内部模型集合。
 *
 * <p>类职责：定义 Java 后端与 Python CLIP 边车服务之间的请求/响应模型。
 */
public final class ClipModels {

    private ClipModels() {}

    /**
     * 发送给 Python CLIP 服务的图片分类请求。
     */
    @Data
    public static class ClipClassifyRequest {

        /** Base64 编码的图片列表，每个元素格式为 "data:<contentType>;base64,<data>" */
        private List<String> images;
    }

    /**
     * Python CLIP 服务返回的单张图片分类结果。
     */
    @Data
    public static class ClipClassifyItem {

        /** 分类标签（5 类之一：group_photo / venue / process / supplies / achievement） */
        private String category;

        /** 分类置信度，范围 [0, 1] */
        private double confidence;
    }

    /**
     * Python CLIP 服务返回的批量分类结果。
     */
    @Data
    public static class ClipClassifyResponse {

        /** 逐图片分类结果，顺序与请求中 images 列表一致 */
        private List<ClipClassifyItem> items;
    }
}
