package io.github.layjason.mayoistar.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跨字段条件校验：根据消息类型（kind）检查对应字段是否提供。
 *
 * <p>类职责：标注需要按消息类型校验内容完整性的请求 DTO。
 *
 * <p>类不变量：应用于 {@link io.github.layjason.mayoistar.api.chat.ChatDtos.SendMessageRequest}。
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MessageContentValidator.class)
public @interface ValidMessageContent {

    /** 校验失败时的默认错误消息。 */
    String message() default "消息内容与消息类型不匹配";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
