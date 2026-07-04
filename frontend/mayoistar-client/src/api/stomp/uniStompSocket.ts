/**
 * uni-app WebSocket 适配器，供 @stomp/stompjs 在 App/小程序端使用。
 *
 * H5 优先使用原生 WebSocket；其余平台使用 uni.connectSocket 封装。
 */
import { StompSocketState, type IStompSocket, type IStompSocketMessageEvent } from '@stomp/stompjs'

/**
 * 将 HTTP API 根地址转为 STOMP WebSocket 端点 URL。
 */
export function toStompWebSocketUrl(baseUrl: string): string {
  const normalized = baseUrl.replace(/\/+$/, '')
  const wsBase = normalized.replace(/^http/i, 'ws')
  return `${wsBase}/chat/ws/messages`
}

/**
 * 创建 STOMP 可用的 WebSocket 实例。
 */
export function createStompWebSocket(url: string): IStompSocket {
  if (typeof WebSocket !== 'undefined') {
    return new WebSocket(url) as unknown as IStompSocket
  }
  return new UniStompSocket(url)
}

/**
 * uni.connectSocket 的 IStompSocket 实现。
 */
class UniStompSocket implements IStompSocket {
  readonly url: string
  binaryType = 'arraybuffer'
  onclose: ((ev?: unknown) => void) | null = null
  onerror: ((ev: unknown) => void) | null = null
  onmessage: ((ev: IStompSocketMessageEvent) => void) | null = null
  onopen: ((ev?: unknown) => void) | null = null

  private _readyState = StompSocketState.CONNECTING
  private task: UniApp.SocketTask

  constructor(url: string) {
    this.url = url
    this.task = uni.connectSocket({ url, complete: () => {} })

    this.task.onOpen(() => {
      this._readyState = StompSocketState.OPEN
      this.onopen?.({})
    })

    this.task.onMessage((res) => {
      this.onmessage?.({ data: res.data as string | ArrayBuffer })
    })

    this.task.onClose(() => {
      this._readyState = StompSocketState.CLOSED
      this.onclose?.({})
    })

    this.task.onError((err) => {
      this.onerror?.(err)
    })
  }

  get readyState(): number {
    return this._readyState
  }

  send(data: string | ArrayBufferLike | Blob | ArrayBufferView): void {
    if (this._readyState !== StompSocketState.OPEN) return
    const payload = typeof data === 'string' ? data : String(data)
    this.task.send({ data: payload })
  }

  close(): void {
    if (this._readyState === StompSocketState.CLOSED) return
    this._readyState = StompSocketState.CLOSING
    this.task.close({})
  }
}
