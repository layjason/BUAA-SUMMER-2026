#!/usr/bin/env python3
"""
QA 测试用户生成脚本

从 backend/.env 读取 PostgreSQL 连接信息，创建指定数量的已激活测试用户
（普通用户和商家用户），并输出生成的用户名和密码。

前置条件：
  - PostgreSQL 服务可用
  - backend/.env 中包含正确的数据库连接配置
  - users 表中的 nickname 和 email 不存在冲突
"""

import argparse
import os
import secrets
import string
import sys
import uuid
from datetime import datetime, timezone

import bcrypt
import dotenv
import psycopg2


def load_db_config() -> dict:
    """从 backend/.env 加载数据库连接配置。"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    env_path = os.path.join(project_root, "backend", ".env")

    if not os.path.exists(env_path):
        print(f"错误: 找不到环境变量文件: {env_path}", file=sys.stderr)
        sys.exit(1)

    dotenv.load_dotenv(env_path)

    return {
        "dbname": os.getenv("POSTGRES_DB", "mayoistar"),
        "user": os.getenv("POSTGRES_USER", "mayoistar"),
        "password": os.getenv("POSTGRES_PASSWORD", "mayoistar"),
        "host": os.getenv("MAYOISTAR_DB_HOST", "localhost"),
        "port": os.getenv("MAYOISTAR_DB_PORT", os.getenv("DEV_POSTGRES_PORT", "5432")),
    }


def generate_password(length: int = 12) -> str:
    """生成由大小写字母和数字组成的随机密码。"""
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def hash_password(plain: str) -> str:
    """使用 BCrypt(12) 哈希密码，与 Java BCryptPasswordEncoder 兼容。"""
    raw = plain.encode("utf-8")
    salt = bcrypt.gensalt(rounds=12)
    return bcrypt.hashpw(raw, salt).decode("utf-8")


def make_email(prefix: str, index: int) -> str:
    """生成测试用户的邮箱地址。"""
    return f"qa_{prefix}_{index}@mayoistar.qa"


def make_nickname(prefix: str, index: int) -> str:
    """生成测试用户的昵称，确保不超过数据库限制（50 字符）。"""
    return f"qa_{prefix}_{index}"


def make_user_values(
    user_id: str,
    email: str,
    nickname: str,
    password_hash: str,
    kind: str,
    now: datetime,
) -> tuple:
    """构造 users 表 INSERT 所需的值元组。"""
    return (
        user_id,
        email,
        nickname,
        password_hash,
        kind,
        "active",
        now,
        now,
        now,
    )


def make_personal_profile_values(user_id: str, signature: str, now: datetime) -> tuple:
    """构造 personal_profiles 表 INSERT 所需的值元组。"""
    return (user_id, "unspecified", signature, 100, now)


def make_merchant_profile_values(user_id: str, merchant_name: str, now: datetime) -> tuple:
    """构造 merchant_profiles 表 INSERT 所需的值元组。"""
    return (user_id, merchant_name, now)


def insert_users(cursor, values: list[tuple]) -> int:
    """批量插入 users 表。若 email 或 nickname 冲突，逐行回退逐条插入并跳过冲突行。"""
    sql = """
        INSERT INTO users (user_id, email, nickname, password_hash, kind,
                           account_status, activated_at, created_at, updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    success = 0
    for val in values:
        try:
            cursor.execute(sql, val)
            success += 1
        except psycopg2.errors.UniqueViolation:
            cursor.connection.rollback()  # 回滚本次冲突，继续下一条
            print(f"  警告: 邮箱或昵称已存在，跳过: {val[1]} / {val[2]}", file=sys.stderr)
    return success


def insert_profiles(cursor, table: str, columns: str, values: list[tuple]) -> int:
    """批量插入 profile 表。"""
    sql = f"INSERT INTO {table} ({columns}) VALUES ({'%s, ' * (len(values[0]) - 1)}%s)"
    success = 0
    for val in values:
        try:
            cursor.execute(sql, val)
            success += 1
        except Exception as e:
            cursor.connection.rollback()
            print(f"  警告: 插入 {table} 失败 ({val[0]}): {e}", file=sys.stderr)
    return success


def main() -> None:
    parser = argparse.ArgumentParser(
        description="创建指定数量的 QA 测试用户（普通用户和商家用户，均已通过邮箱验证）"
    )
    parser.add_argument(
        "--personal",
        type=int,
        default=5,
        help="普通用户数量（默认 5）",
    )
    parser.add_argument(
        "--merchant",
        type=int,
        default=5,
        help="商家用户数量（默认 5）",
    )
    args = parser.parse_args()

    db_config = load_db_config()

    # 连接数据库
    try:
        conn = psycopg2.connect(**db_config)
    except psycopg2.Error as e:
        print(f"错误: 无法连接数据库: {e}", file=sys.stderr)
        sys.exit(1)

    now = datetime.now(timezone.utc)
    personal_credentials: list[tuple[str, str]] = []  # (email, password)
    merchant_credentials: list[tuple[str, str]] = []

    cursor = conn.cursor()

    # --- 普通用户 ---
    if args.personal > 0:
        print(f"正在创建 {args.personal} 个普通用户...")
        user_values = []
        profile_values = []
        for i in range(1, args.personal + 1):
            uid = str(uuid.uuid4())
            email = make_email("personal", i)
            nickname = make_nickname("personal", i)
            password = generate_password()
            pwd_hash = hash_password(password)
            signature = f"QA 测试用户 #{i}"

            user_values.append(make_user_values(uid, email, nickname, pwd_hash, "personal", now))
            profile_values.append(make_personal_profile_values(uid, signature, now))
            personal_credentials.append((email, password))

        n_users = insert_users(cursor, user_values)
        n_profiles = insert_profiles(
            cursor,
            "personal_profiles",
            "user_id, gender, signature, reputation_score, updated_at",
            profile_values,
        )
        conn.commit()
        print(f"  普通用户: users 插入 {n_users} 条, personal_profiles 插入 {n_profiles} 条")

    # --- 商家用户 ---
    if args.merchant > 0:
        print(f"正在创建 {args.merchant} 个商家用户...")
        user_values = []
        profile_values = []
        for i in range(1, args.merchant + 1):
            uid = str(uuid.uuid4())
            email = make_email("merchant", i)
            nickname = make_nickname("merchant", i)
            password = generate_password()
            pwd_hash = hash_password(password)
            merchant_name = f"QA 测试商家 #{i}"

            user_values.append(make_user_values(uid, email, nickname, pwd_hash, "merchant", now))
            profile_values.append(make_merchant_profile_values(uid, merchant_name, now))
            merchant_credentials.append((email, password))

        n_users = insert_users(cursor, user_values)
        n_profiles = insert_profiles(
            cursor,
            "merchant_profiles",
            "user_id, merchant_name, updated_at",
            profile_values,
        )
        conn.commit()
        print(f"  商家用户: users 插入 {n_users} 条, merchant_profiles 插入 {n_profiles} 条")

    cursor.close()
    conn.close()

    # --- 输出结果 ---
    print()
    if personal_credentials:
        print("普通用户:")
        for email, password in personal_credentials:
            print(f"  {email} / {password}")
    if merchant_credentials:
        print()
        print("商家用户:")
        for email, password in merchant_credentials:
            print(f"  {email} / {password}")
    print()
    print(f"完成。共处理 {len(personal_credentials)} 个普通用户, {len(merchant_credentials)} 个商家用户。")


if __name__ == "__main__":
    main()
