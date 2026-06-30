import { describe, expect, it } from 'vitest';
import { readActivationToken, buildAppActivationUrl } from './activationUtils';

describe('readActivationToken', () => {
  it('从标准查询字符串中提取 token', () => {
    expect(readActivationToken('?token=abc123')).toBe('abc123');
  });

  it('不含 ? 前缀的查询字符串也能正确解析', () => {
    expect(readActivationToken('token=xyz789')).toBe('xyz789');
  });

  it('token 前后空格被去除', () => {
    expect(readActivationToken('?token=%20hello%20')).toBe('hello');
  });

  it('token 参数缺失时返回 null', () => {
    expect(readActivationToken('?foo=bar')).toBeNull();
  });

  it('token 为空字符串时返回 null', () => {
    expect(readActivationToken('?token=')).toBeNull();
  });

  it('token 只含空格时返回 null', () => {
    expect(readActivationToken('?token=%20%20')).toBeNull();
  });

  it('空查询字符串返回 null', () => {
    expect(readActivationToken('')).toBeNull();
  });
});

describe('buildAppActivationUrl', () => {
  it('token 非空时返回带编码 token 的深度链接', () => {
    expect(buildAppActivationUrl('abc123')).toBe('mayoistar://pages/activate/index?token=abc123');
  });

  it('token 含特殊字符时进行 URL 编码', () => {
    expect(buildAppActivationUrl('a b+c')).toBe('mayoistar://pages/activate/index?token=a%20b%2Bc');
  });

  it('token 为 null 时返回基础深度链接', () => {
    expect(buildAppActivationUrl(null)).toBe('mayoistar://pages/activate/index');
  });
});
