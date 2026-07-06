import { describe, expect, it } from 'vitest';
import {
  readResetToken,
  buildAppUrl,
  isValidEmail,
  isValidPassword,
  MIN_PASSWORD_LENGTH,
} from './passwordResetUtils';

describe('readResetToken', () => {
  it('从标准查询字符串中提取 token', () => {
    expect(readResetToken('?token=reset-abc123')).toBe('reset-abc123');
  });

  it('不含 ? 前缀的查询字符串也能正确解析', () => {
    expect(readResetToken('token=xyz789')).toBe('xyz789');
  });

  it('token 前后空格被去除', () => {
    expect(readResetToken('?token=%20hello%20')).toBe('hello');
  });

  it('token 参数缺失时返回 null', () => {
    expect(readResetToken('?foo=bar')).toBeNull();
  });

  it('token 为空字符串时返回 null', () => {
    expect(readResetToken('?token=')).toBeNull();
  });

  it('token 只含空格时返回 null', () => {
    expect(readResetToken('?token=%20%20')).toBeNull();
  });

  it('空查询字符串返回 null', () => {
    expect(readResetToken('')).toBeNull();
  });
});

describe('buildAppUrl', () => {
  it('forgot-password 无 token 时返回基础路径', () => {
    expect(buildAppUrl('forgot-password')).toBe('mayoistar://pages/forgot-password/index');
  });

  it('forgot-password 传 null 时返回基础路径', () => {
    expect(buildAppUrl('forgot-password', null)).toBe('mayoistar://pages/forgot-password/index');
  });

  it('reset-password 带 token 时返回编码后的深度链接', () => {
    expect(buildAppUrl('reset-password', 'abc123')).toBe(
      'mayoistar://pages/reset-password/index?token=abc123',
    );
  });

  it('reset-password token 含特殊字符时进行 URL 编码', () => {
    expect(buildAppUrl('reset-password', 'a b+c')).toBe(
      'mayoistar://pages/reset-password/index?token=a%20b%2Bc',
    );
  });

  it('reset-password token 为 null 时返回基础路径', () => {
    expect(buildAppUrl('reset-password', null)).toBe('mayoistar://pages/reset-password/index');
  });

  it('forgot-password 带 token 时也正确拼接', () => {
    expect(buildAppUrl('forgot-password', 'tok123')).toBe(
      'mayoistar://pages/forgot-password/index?token=tok123',
    );
  });
});

describe('isValidEmail', () => {
  it('合法邮箱返回 true', () => {
    expect(isValidEmail('user@example.com')).toBe(true);
  });

  it('带子域名的邮箱返回 true', () => {
    expect(isValidEmail('user@sub.example.com')).toBe(true);
  });

  it('前后空格被忽略', () => {
    expect(isValidEmail('  user@example.com  ')).toBe(true);
  });

  it('缺少 @ 返回 false', () => {
    expect(isValidEmail('userexample.com')).toBe(false);
  });

  it('缺少域名返回 false', () => {
    expect(isValidEmail('user@')).toBe(false);
  });

  it('空字符串返回 false', () => {
    expect(isValidEmail('')).toBe(false);
  });

  it('含空格返回 false', () => {
    expect(isValidEmail('user @example.com')).toBe(false);
  });
});

describe('isValidPassword', () => {
  it('满足最低长度要求时返回 true', () => {
    expect(isValidPassword('a'.repeat(MIN_PASSWORD_LENGTH))).toBe(true);
  });

  it('超过最低长度时返回 true', () => {
    expect(isValidPassword('a'.repeat(MIN_PASSWORD_LENGTH + 5))).toBe(true);
  });

  it('低于最低长度时返回 false', () => {
    expect(isValidPassword('a'.repeat(MIN_PASSWORD_LENGTH - 1))).toBe(false);
  });

  it('空密码返回 false', () => {
    expect(isValidPassword('')).toBe(false);
  });
});
