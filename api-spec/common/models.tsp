import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi;

@doc("平台统一 JSON 成功响应，code 固定为 200，data 承载强类型业务数据。")
model APIResponse<ResponseData> {
  @doc("平台响应代码，成功响应固定为 200。")
  code: 200;

  @doc("平台响应消息，成功响应固定为 For Super Earth!。")
  message: "For Super Earth!";

  @doc("响应数据。")
  data: ResponseData;
}

@doc("平台统一 JSON 错误响应，code 与 message 由通用错误或业务错误类型固定，data 默认为空对象。")
model APIErrorResponse<
  ErrorCode extends int32,
  ErrorMessage extends string,
  ErrorData = EmptyData
> {
  @doc("平台错误代码，小于 1000 为通用错误代码，大于等于 10000 为业务错误代码。")
  code: ErrorCode;

  @doc("平台错误消息，业务错误使用英文模板文案。")
  message: ErrorMessage;

  @doc("错误上下文，默认无额外业务数据。")
  data: ErrorData;
}

@doc("请求体或 URL 参数无法解析，或不符合 TypeSpec Schema。")
model BadRequestResponse extends APIErrorResponse<400, "{reason}"> {}

@doc("调用方未提供有效 Bearer Token，或 Token 已过期。")
model UnauthorizedResponse
  extends APIErrorResponse<401, "Authentication is required"> {}

@doc("调用方已认证，但没有执行该操作的权限。")
model ForbiddenResponse extends APIErrorResponse<403, "Permission is denied"> {}

@doc("内部服务器错误。")
model InternalServerErrorResponse
  extends APIErrorResponse<500, "An internal server error has occurred"> {}

// 所有 JSON API 共享的通用错误响应。
alias CommonAPIError =
  | BadRequestResponse
  | UnauthorizedResponse
  | ForbiddenResponse
  | InternalServerErrorResponse;

// JSON API 统一响应联合类型，包含成功响应、通用错误响应和接口声明的业务错误响应。
alias APIResult<ResponseData, BusinessErrors = never> =
  | APIResponse<ResponseData>
  | CommonAPIError
  | BusinessErrors;

@doc("空响应数据，接口只需表达处理结果，无额外业务数据。")
model EmptyData {}

@doc("分页请求，page 从 1 开始，pageSize 为正数，服务端按页返回结果，同一查询条件下排序稳定。")
model PageQuery {
  @query
  @doc("页码，从 1 开始。")
  page?: int32 = 1;

  @query
  @doc("每页数量。")
  pageSize?: int32 = 20;
}

@doc("分页响应，items 来自同一查询条件，total 表示匹配总数，totalPages 表示匹配总页数，items 长度不超过 pageSize。")
model PageResult<Item> {
  @doc("当前页数据。")
  items: Item[];

  @doc("匹配总数。")
  total: int64;

  @doc("当前页码。")
  page: int32;

  @doc("每页数量。")
  pageSize: int32;

  @doc("匹配总页数。")
  totalPages: int32;
}

@doc("平台通用实体标识。")
scalar EntityId extends string;

@doc("ISO 8601 日期时间字符串，例如 2023-10-05T14:30:00Z。")
scalar DateTimeString extends string;

@doc("ISO 8601 日期字符串，例如 2023-10-05。")
scalar DateString extends string;

@doc("一天内从 00:00:00 开始计算的秒数。")
scalar TimeOfDaySeconds extends int32;

@doc("地理坐标，经纬度来自地图选点或可信定位，可用于距离计算与地图展示，longitude 为经度，latitude 为纬度。")
model GeoPoint {
  @doc("经度。")
  longitude: float64;

  @doc("纬度。")
  latitude: float64;
}

@doc("地点信息，创建活动或位置消息时提供，支持地图展示和距离检索，point 与 address 描述同一地点。")
model LocationInfo {
  @doc("地图坐标。")
  point: GeoPoint;

  @doc("城市名称。")
  city: string;

  @doc("详细地址。")
  address: string;

  @doc("地点展示名称。")
  placeName?: string;
}

@doc("媒体用途。")
enum MediaUsage {
  @doc("用户/小队头像。")
  avatar,

  @doc("商家营业凭证。")
  merchantLicense,

  @doc("活动图片。")
  activityImage,

  @doc("即时通信中发送的图片。")
  chatImage,

  @doc("小队文件。")
  teamFile,

  @doc("小队相册。")
  teamAlbum,

  @doc("活动总结中的图片。")
  summaryImage,

  @doc("活动评价 Markdown 内链图片。")
  activityReviewImage,
}

@doc("媒体文件元数据，文件已完成上传，业务接口可通过 mediaId 引用，不暴露存储凭据。")
model MediaFile {
  @doc("媒体文件标识。")
  mediaId: EntityId;

  @doc("原始文件名。")
  fileName: string;

  @doc("MIME 类型。")
  contentType: string;

  @doc("文件大小，单位字节。")
  sizeBytes: int64;

  @doc("媒体用途。")
  usage: MediaUsage;

  @doc("访问地址。")
  url?: string;

  @doc("上传时间。")
  uploadedAt: DateTimeString;
}

@doc("通用媒体上传请求，调用方使用 multipart/form-data，服务端根据调用端点自动设置媒体用途，业务对象只保存 mediaId。")
model MediaUploadRequest {
  @doc("上传文件。")
  file: HttpPart<bytes>;
}

@route("/media")
@tag("通用")
interface CommonOperations {
  @get
  @route("/{mediaId}")
  @doc("根据媒体文件标识获取文件内容，成功时固定返回 200 和原始二进制流，Content-Type 根据上传时的 MIME 类型设置。")
  getMediaFile(@path @doc("媒体文件标识") mediaId: EntityId): {
    @body body: bytes;
    @header contentType: string;
  };
}

@doc("审核状态。")
enum ReviewStatus {
  @doc("等待审核。")
  pending,

  @doc("审核通过。")
  approved,

  @doc("审核驳回。")
  rejected,

  @doc("需要修改。")
  changeRequired,
}

@doc("人工审核请求，审核对象存在且处于可审核状态，对象状态按 result 迁移，驳回、要求修改、下架、封禁类动作必须提供 reason。")
model ReviewDecisionRequest {
  @doc("审核结果。")
  result: ReviewStatus;

  @doc("审核原因或修改意见。")
  reason?: string;
}

@doc("审核记录。")
model ReviewRecord {
  @doc("审核记录标识。")
  reviewId: EntityId;

  @doc("审核结果。")
  result: ReviewStatus;

  @doc("审核原因或修改意见。")
  reason?: string;

  @doc("审核人标识，自动审核时为空。")
  reviewerId?: EntityId;

  @doc("审核时间。")
  reviewedAt: DateTimeString;
}
