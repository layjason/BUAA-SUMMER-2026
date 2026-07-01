package io.github.layjason.mayoistar.api.validation;

import static io.github.layjason.mayoistar.exception.ErrorCodes.MESSAGE_CONTENT_INVALID;

import io.github.layjason.mayoistar.api.chat.ChatDtos;
import io.github.layjason.mayoistar.exception.BusinessException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 根据消息类型（kind）校验请求字段是否完整。
 *
 * <p>前置条件：request.getKind() 已通过 @NotNull 校验。
 *
 * <p>后置条件：类型匹配的字段均已提供时通过，否则抛出 BusinessException(50006)。
 *
 * <p>类不变量：校验完全基于请求字段，不访问数据库或外部资源。
 */
public class MessageContentValidator implements ConstraintValidator<ValidMessageContent, ChatDtos.SendMessageRequest> {

    @Override
    public boolean isValid(ChatDtos.SendMessageRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getKind() == null) {
            return true;
        }
        switch (request.getKind()) {
            case text -> {
                if (request.getText() == null || request.getText().isBlank()) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
            case image -> {
                if (request.getImageMediaId() == null
                        || request.getImageMediaId().isBlank()) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
            case location -> {
                if (request.getLocation() == null
                        || request.getLocation().getPoint() == null
                        || request.getLocation().getPoint().getLongitude() == null
                        || request.getLocation().getPoint().getLatitude() == null) {
                    throw new BusinessException(MESSAGE_CONTENT_INVALID, "Message content is invalid for its kind");
                }
            }
        }
        return true;
    }
}
