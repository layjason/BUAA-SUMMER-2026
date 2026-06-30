import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Identity;

@doc("用户类型。")
enum UserKind {
  personal,
  merchant,
}

@doc("账号状态，控制登录权限。")
enum AccountStatus {
  inactive,
  active,
  banned,
}

@doc("商家资质审核状态。")
enum QualificationStatus {
  not_submitted,
  pending,
  approved,
  rejected,
}

@doc("性别。")
enum Gender {
  unspecified,
  female,
  male,
  other,
}

@doc("登录凭据，邮箱与密码来自用户输入，认证成功时返回 JWT，契约不传输密码哈希或盐。")
model EmailPasswordRequest {
  @doc("登录邮箱。")
  email: string;

  @doc("用户输入的明文密码，仅用于本次认证请求。")
  password: string;
}

@doc("个人用户注册请求，邮箱未注册且昵称全平台唯一，创建未激活账号并发送激活邮件，服务端负责密码加盐哈希。")
model PersonalRegisterRequest {
  @doc("注册邮箱，用于登录和接收激活邮件。")
  email: string;

  @doc("用户设置的明文密码，服务端负责加盐哈希保存。")
  password: string;

  @doc("个人用户昵称，全平台唯一。")
  nickname: string;
}

@doc("商家注册请求，邮箱未注册，创建未激活账号并发送激活邮件，商家资料和资质提交走独立接口。")
model MerchantRegisterRequest {
  @doc("商家账号注册邮箱，用于登录和接收激活邮件。")
  email: string;

  @doc("商家账号明文密码，服务端负责加盐哈希保存。")
  password: string;
}

@doc("令牌响应。")
model TokenPair {
  @doc("访问令牌，用于 Bearer 鉴权。")
  accessToken: string;

  @doc("刷新令牌，用于换取新的访问令牌。")
  refreshToken: string;

  @doc("访问令牌过期时间。")
  expiresAt: DateTimeString;
}

@doc("登录响应。")
model LoginResult {
  @doc("登录用户标识。")
  userId: EntityId;

  @doc("登录用户类型。")
  kind: UserKind;

  @doc("当前账号状态。")
  accountStatus: AccountStatus;

  @doc("登录成功后签发的令牌对。")
  tokens: TokenPair;
}

@doc("刷新令牌请求，refreshToken 未过期且未被撤销，签发新的访问令牌，不会改变账号资料。")
model RefreshTokenRequest {
  @doc("用于刷新访问令牌的 refreshToken。")
  refreshToken: string;
}

@doc("修改密码请求，调用方已登录且当前密码正确，密码更新后服务端可使旧令牌失效，新密码只保存加盐哈希。")
model ChangePasswordRequest {
  @doc("当前密码。")
  oldPassword: string;

  @doc("新密码。")
  newPassword: string;
}

@doc("密码重置邮件请求，邮箱用于接收一次性密码重置链接，接口不得向未认证调用方暴露账号是否存在。")
model PasswordResetEmailRequest {
  @doc("需要接收密码重置邮件的账号邮箱。")
  email: string;
}

@doc("重置密码请求，token 来自密码重置邮件且未过期未使用，重置成功后新密码只保存加盐哈希。")
model ResetPasswordRequest {
  @doc("密码重置邮件中的一次性 token。")
  token: string;

  @doc("新密码。")
  newPassword: string;
}

@doc("兴趣标签。")
model InterestTagItem {
  @doc("标签名称，例如「运动」。")
  name: string;
}

@doc("用户公开资料。")
model PublicUserProfile {
  @doc("用户标识。")
  userId: EntityId;

  @doc("用户展示昵称。")
  nickname: string;

  @doc("用户头像媒体文件。")
  avatar?: MediaFile;

  @doc("用户性别。")
  gender?: Gender;

  @doc("用户生日。")
  birthday?: DateString;

  @doc("用户个性签名。")
  signature?: string;

  @doc("用户兴趣标签。")
  interestTags: string[];

  @doc("用户信誉分，用于报名等业务校验。")
  reputationScore: int32;

  @doc("用户类型。")
  kind: UserKind;
}

@doc("个人资料更新请求，调用方为本人，资料被部分更新且昵称仍唯一，不得通过该接口修改密码和账号状态。")
model UpdatePersonalProfileRequest {
  @doc("新头像媒体文件标识。")
  avatarMediaId?: EntityId;

  @doc("新昵称，全平台唯一。")
  nickname?: string;

  @doc("新性别。")
  gender?: Gender;

  @doc("新生日。")
  birthday?: DateString;

  @doc("新个性签名。")
  signature?: string;

  @doc("新的兴趣标签名称列表。")
  interestTags?: string[];
}

@doc("商家资质详情。")
model QualificationDetail {
  @doc("资质审核状态。")
  status: QualificationStatus;

  @doc("提交时间。")
  submittedAt?: DateTimeString;

  @doc("审核时间。")
  reviewedAt?: DateTimeString;

  @doc("驳回原因。")
  rejectReason?: string;

  @doc("营业凭证图片 URL 列表。")
  licenseImageUrls?: string[];
}

@doc("商家资料。")
model MerchantProfile {
  @doc("商家用户标识。")
  userId: EntityId;

  @doc("商家主体名称。")
  merchantName: string;

  @doc("商家平台展示昵称。")
  merchantNickname: string;

  @doc("商家头像媒体文件。")
  avatar?: MediaFile;

  @doc("商家关注或经营的活动领域。")
  interestedActivityFields: string[];

  @doc("账号状态。")
  accountStatus: AccountStatus;

  @doc("资质审核状态。")
  qualificationStatus: QualificationStatus;

  @doc("资质详情，含提交/审核时间和凭证图片 URL。若无资质记录则返回 null。")
  qualification?: QualificationDetail;
}

@doc("商家资料更新请求，调用方为商家本人，商家资料被部分更新，资质审核状态只能由后台流程改变。")
model UpdateMerchantProfileRequest {
  @doc("新的商家主体名称。")
  merchantName?: string;

  @doc("新的商家展示昵称。")
  merchantNickname?: string;

  @doc("新头像媒体文件标识。")
  avatarMediaId?: EntityId;

  @doc("新的关注或经营活动领域。")
  interestedActivityFields?: string[];
}

@doc("昵称校验结果。")
model NicknameAvailability {
  @doc("被检查的昵称。")
  nickname: string;

  @doc("该昵称是否可注册或使用。")
  available: boolean;
}

@doc("账号激活请求。")
model AccountActivationRequest {
  @doc("激活邮件中的一次性激活 token。")
  token: string;
}

@doc("重新发送激活邮件请求。")
model ResendActivationEmailRequest {
  @doc("需要重新接收激活邮件的账号邮箱。")
  email: string;
}

@doc("商家资质提交请求，上传营业执照或营业凭证图片，请求为 multipart/form-data，支持多文件。")
model QualificationSubmitRequest {
  @doc("营业执照或营业凭证图片。")
  licenseImages: HttpPart<bytes>[];
}

@doc("头像上传请求，请求为 multipart/form-data，上传成功后服务端固定媒体用途为 avatar。")
model AvatarUploadRequest {
  @doc("头像文件。")
  file: HttpPart<bytes>;
}

@route("/identity")
@tag("身份与资料")
interface IdentityOperations {
  @post
  @route("/media/license")
  @doc("上传商家营业执照或营业凭证，请求为 multipart/form-data，返回可用于后续资质提交的 mediaId，不得返回存储凭据。")
  uploadMerchantLicense(@multipartBody body: MediaUploadRequest): APIResult<
    MediaFile,

      | QujuApi.Errors.Identity.InvalidMediaUsage
      | QujuApi.Errors.Identity.ImageFormatInvalid
      | QujuApi.Errors.Identity.ImageTooLarge
  >;

  @post
  @route("/media/avatar")
  @doc("上传用户或商家头像，请求为 multipart/form-data，服务端固定媒体用途为 avatar，资料更新接口通过 avatarMediaId 引用。")
  uploadAvatar(
    @multipartBody body: AvatarUploadRequest,
  ): APIResponse<MediaFile>;

  @post
  @route("/auth/register/personal")
  @doc("注册个人用户，邮箱未注册且昵称唯一，账号进入未激活状态并触发激活邮件，密码只在服务端加盐哈希保存。")
  registerPersonal(@body body: PersonalRegisterRequest): APIResult<
    EmptyData,

      | QujuApi.Errors.Identity.EmailAlreadyRegistered
      | QujuApi.Errors.Identity.NicknameUnavailable
  >;

  @post
  @route("/auth/register/merchant")
  @doc("注册商家用户，邮箱未注册，账号进入未激活状态并触发激活邮件，商家资料和资质提交走独立接口。")
  registerMerchant(@body body: MerchantRegisterRequest): APIResult<
    EmptyData,
    QujuApi.Errors.Identity.EmailAlreadyRegistered
  >;

  @post
  @route("/auth/login")
  @doc("邮箱密码登录，账号已激活且未封禁，返回 JWT 令牌，封禁账号不得登录。")
  login(@body body: EmailPasswordRequest): APIResult<
    LoginResult,

      | QujuApi.Errors.Identity.InvalidCredentials
      | QujuApi.Errors.Identity.AccountInactive
      | QujuApi.Errors.Identity.AccountBanned
  >;

  @post
  @route("/auth/logout")
  @doc("登出当前用户，作废该用户所有 Refresh Token。")
  logout(): APIResult<EmptyData>;

  @post
  @route("/auth/activate")
  @doc("激活账号，激活 token 有效且未使用，账号从 inactive 变更为 active，token 只能使用一次。")
  activateAccount(@body body: AccountActivationRequest): APIResult<
    EmptyData,

      | QujuApi.Errors.Identity.InvalidActivationToken
      | QujuApi.Errors.Identity.AccountAlreadyActive
  >;

  @post
  @route("/auth/activation-email")
  @doc("重新发送激活邮件，邮箱对应账号未激活，发送新的激活链接，不暴露邮件服务凭据。")
  resendActivationEmail(@body body: ResendActivationEmailRequest): APIResult<
    EmptyData,

      | QujuApi.Errors.Identity.EmailNotRegistered
      | QujuApi.Errors.Identity.AccountAlreadyActive
      | QujuApi.Errors.Identity.ActivationEmailRateLimited
  >;

  @post
  @route("/auth/refresh")
  @doc("刷新访问令牌，刷新令牌有效，返回新的令牌对，不会绕过账号封禁校验。")
  refreshToken(@body body: RefreshTokenRequest): APIResult<
    TokenPair,

      | QujuApi.Errors.Identity.InvalidRefreshToken
      | QujuApi.Errors.Identity.AccountBanned
  >;

  @post
  @route("/auth/password-reset-email")
  @doc("发送密码重置邮件，邮箱若属于个人用户或商户账号则发送一次性重置链接，响应不得向未认证调用方暴露账号是否存在。")
  sendPasswordResetEmail(@body body: PasswordResetEmailRequest): APIResult<
    EmptyData,
    QujuApi.Errors.Identity.PasswordResetEmailRateLimited
  >;

  @post
  @route("/auth/password-reset")
  @doc("通过邮件链接重置个人用户或商户密码，重置 token 有效且未使用，密码更新后旧登录凭据失效。")
  resetPassword(@body body: ResetPasswordRequest): APIResult<
    EmptyData,

      | QujuApi.Errors.Identity.InvalidPasswordResetToken
      | QujuApi.Errors.Identity.AccountBanned
  >;

  @get
  @route("/nicknames/availability")
  @doc("校验昵称是否可用，nickname 非空，返回唯一性校验结果，个人昵称和商家昵称共享唯一性约束。")
  checkNickname(@query nickname: string): APIResult<NicknameAvailability>;

  @get
  @route("/interest-tags")
  @doc("获取系统预定义的所有兴趣标签列表，供用户选择。")
  getInterestTags(): APIResult<InterestTagItem[]>;

  @get
  @route("/me/profile")
  @doc("获取本人公开资料，调用方已登录，返回当前账号资料，不返回密码、盐和敏感凭据。")
  getMyProfile(): APIResult<
    PublicUserProfile,
    QujuApi.Errors.Identity.UserKindMismatch
  >;

  @patch
  @route("/me/profile")
  @doc("更新个人资料，调用方为个人用户，资料字段被部分更新，昵称仍保持全平台唯一。")
  updatePersonalProfile(@body body: UpdatePersonalProfileRequest): APIResult<
    PublicUserProfile,

      | QujuApi.Errors.Identity.UserKindMismatch
      | QujuApi.Errors.Identity.NicknameUnavailable
      | QujuApi.Errors.Identity.MediaFileUnavailable
  >;

  @post
  @route("/me/password")
  @doc("修改当前登录个人用户或商户密码，当前密码正确后更新密码，密码只保存加盐哈希。")
  changePassword(@body body: ChangePasswordRequest): APIResult<
    EmptyData,

      | QujuApi.Errors.Identity.InvalidOldPassword
      | QujuApi.Errors.Identity.UserKindMismatch
      | QujuApi.Errors.Identity.AccountBanned
  >;

  @get
  @route("/me/merchant-profile")
  @doc("获取本人商家资料，调用方为商家用户，返回商家资料与资质审核状态，不返回凭据原文。")
  getMyMerchantProfile(): APIResult<
    MerchantProfile,
    QujuApi.Errors.Identity.UserKindMismatch
  >;

  @patch
  @route("/me/merchant-profile")
  @doc("更新商家资料，调用方为商家用户，资料被部分更新，资质审核状态只能由后台流程改变。")
  updateMerchantProfile(@body body: UpdateMerchantProfileRequest): APIResult<
    MerchantProfile,

      | QujuApi.Errors.Identity.UserKindMismatch
      | QujuApi.Errors.Identity.NicknameUnavailable
      | QujuApi.Errors.Identity.MediaFileUnavailable
  >;

  @post
  @route("/me/merchant-qualification")
  @doc("提交商家资质申请，请求为 multipart/form-data，上传营业执照或营业凭证图片。若已有 pending 或 approved 的申请则拒绝重复提交，rejected 后可重新提交。仅商家用户可调用。")
  submitMerchantQualification(
    @multipartBody body: QualificationSubmitRequest,
  ): APIResult<
    EmptyData,

      | QujuApi.Errors.Identity.UserKindMismatch
      | QujuApi.Errors.Identity.MerchantQualificationAlreadySubmitted
      | QujuApi.Errors.Identity.InvalidMediaUsage
      | QujuApi.Errors.Identity.ImageFormatInvalid
      | QujuApi.Errors.Identity.ImageTooLarge
  >;
}
