package io.github.layjason.mayoistar.service.storage;

import java.io.InputStream;

/**
 * 文件存储服务抽象接口。
 *
 * <p>类职责：定义对象存储的基础操作，与具体存储实现（本地/S3/其他）解耦。
 *
 * <p>不变量：所有实现必须保证 key 作为存储中对象的唯一标识。
 */
public interface FileStorageService {

    /**
     * 存储文件并返回对象 key。
     *
     * <p>前置条件：data 可读取，contentType 和 size 非空。
     *
     * <p>后置条件：文件已存入存储服务，可通过同一 key 检索。
     *
     * @param key         存储对象的唯一标识
     * @param data        文件输入流
     * @param contentType MIME 类型
     * @param size        文件字节数
     * @return 存储成功的对象 key
     */
    String store(String key, InputStream data, String contentType, long size);

    /**
     * 读取文件内容。
     *
     * <p>前置条件：key 对应的对象存在。
     *
     * <p>后置条件：返回文件的输入流，调用方负责关闭。
     *
     * @param key 存储对象的唯一标识
     * @return 文件输入流
     */
    InputStream retrieve(String key);

    /**
     * 获取文件的公开访问 URL。
     *
     * <p>前置条件：key 对应的对象存在，bucket 为公开读。
     *
     * <p>后置条件：返回可直接访问文件的 URL 字符串。
     *
     * @param key 存储对象的唯一标识
     * @return 公开访问 URL
     */
    String getPublicUrl(String key);

    /**
     * 删除文件。
     *
     * <p>前置条件：key 对应的对象存在。
     *
     * <p>后置条件：对象已从存储服务中删除。
     *
     * @param key 存储对象的唯一标识
     */
    void delete(String key);
}
