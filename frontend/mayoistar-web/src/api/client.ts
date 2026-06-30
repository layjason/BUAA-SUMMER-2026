import type { APIResultEnvelope, PaginatedResult } from '../types';

/**
 * 业务错误，携带后端返回的业务错误码与消息。
 *
 * 前置条件：后端响应 code !== 200 时构造该错误。
 * 后置条件：调用方可通过 instanceof 区分业务错误与网络错误，并读取 code 做分支处理。
 * 不变量：不修改原始响应数据，只保存 code 与 message 副本。
 */
export class BusinessError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'BusinessError';
    this.code = code;
  }
}

const ACCESS_TOKEN_STORAGE_KEY = 'mayoistar_admin_access_token';

let accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY) || '';

/**
 * 读取当前内存中的后台访问令牌。
 *
 * 前置条件：浏览器环境已经初始化 localStorage。
 * 后置条件：返回当前访问令牌，未登录时返回空字符串。
 * 不变量：该函数不修改内存或持久化状态。
 */
export const getAccessToken = () => accessToken;

/**
 * 同步更新内存和本地存储中的后台访问令牌。
 *
 * 前置条件：token 为服务端签发的 Bearer Token，或为空字符串表示退出登录。
 * 后置条件：非空 token 被写入 localStorage，空 token 会清除本地存储。
 * 不变量：只维护访问令牌，不修改其它业务数据。
 */
export const setAccessToken = (token: string) => {
  accessToken = token;
  if (token) {
    localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  }
};

/**
 * 判断当前是否启用本地 Mock 数据。
 *
 * 前置条件：Vite 环境变量已加载。
 * 后置条件：仅当 VITE_USE_MOCK 明确为 false 时返回 false，其余情况默认启用 Mock。
 * 不变量：该函数不读取网络状态，不触发请求。
 */
export const isMockMode = () => {
  if (import.meta.env.VITE_USE_MOCK === 'false') {
    return false;
  }
  return true;
};

type ToastCallback = (message: string, type: 'success' | 'error' | 'info') => void;
let toastHandler: ToastCallback | null = null;
/**
 * 注册 API 层可复用的 Toast 回调。
 *
 * 前置条件：调用方提供的 handler 可以处理成功、错误和信息提示。
 * 后置条件：后续 API 层消息会通过该回调派发。
 * 不变量：只替换回调引用，不主动展示消息。
 */
export const registerToastHandler = (handler: ToastCallback) => {
  toastHandler = handler;
};

/**
 * 展示 API 层提示消息。
 *
 * 前置条件：message 为需要展示给操作者的简短文本。
 * 后置条件：已注册 Toast 时展示提示，否则写入控制台。
 * 不变量：该函数不改变请求状态和认证状态。
 */
export const showToast = (message: string, type: 'success' | 'error' | 'info' = 'info') => {
  if (toastHandler) {
    toastHandler(message, type);
  } else {
    console.log(`[Toast ${type}]: ${message}`);
  }
};

const apiBaseUrl = () => import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? '';

const toErrorMessage = (error: unknown) =>
  error instanceof Error ? error.message : '未知请求错误';

/**
 * 发送统一包装格式的 API 请求。
 *
 * 前置条件：path 为绝对 URL，或为以 / 开头的后端相对路径；后端返回 APIResponse 包装格式。
 * 后置条件：业务成功时返回 data，HTTP 或业务错误时抛出 Error。
 * 不变量：除必要的认证头和 Content-Type 外，不修改调用方传入的请求体。
 *
 * @template ResponseData 后端成功响应 data 字段的类型
 */
export async function request<ResponseData>(
  path: string,
  options: RequestInit = {},
): Promise<ResponseData> {
  const headers = new Headers(options.headers || {});

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  if (!(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const url = path.startsWith('http') ? path : `${apiBaseUrl()}${path}`;

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const errMsg = `HTTP Error ${response.status}: ${response.statusText}`;
      showToast(errMsg, 'error');
      throw new Error(errMsg);
    }

    const result: APIResultEnvelope<ResponseData> = await response.json();

    if (result.code !== 200) {
      showToast(result.message || '请求执行失败', 'error');
      throw new BusinessError(result.code, result.message || 'API Error');
    }

    if (result.message && result.message !== 'For Super Earth!') {
      showToast(result.message, 'success');
    }

    return result.data;
  } catch (error: unknown) {
    console.error('API Request failed:', error);
    if (!toastHandler) {
      console.error(toErrorMessage(error));
    }
    throw error;
  }
}

/**
 * 为本地 Mock 请求模拟网络延迟。
 *
 * 前置条件：ms 为非负等待毫秒数。
 * 后置条件：返回的 Promise 在指定时间后 resolve。
 * 不变量：不触发真实网络请求。
 */
export function simulateLatency(ms = 250): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 构造符合契约的分页响应数据。
 *
 * 前置条件：items 已按 page 和 pageSize 完成切片，total 为切片前的匹配总数，page 和 pageSize 为正数。
 * 后置条件：返回包含 totalPages 的 PageResult 结构。
 * 不变量：不修改 items 数组内容，只根据 total 和 pageSize 计算总页数。
 *
 * @template Item 分页列表元素类型
 */
export function buildPaginatedResult<Item>(
  items: Item[],
  total: number,
  page: number,
  pageSize: number,
): PaginatedResult<Item> {
  return {
    items,
    total,
    page,
    pageSize,
    totalPages: Math.ceil(total / pageSize) || 1,
  };
}
