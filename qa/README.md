# QA 工具

本目录存放 QA（质量保证）相关的工具和测试资源。

## 目录结构

```
qa/
├── seed_qa_users.py   # 批量创建测试用户脚本
├── yaak/              # Yaak / Postman API 测试集合
├── pyproject.toml     # Python 项目管理（uv）
├── uv.lock            # 依赖锁定文件
└── .venv/             # 虚拟环境（由 uv 管理）
```

## 环境准备

```bash
# 安装 uv（如尚未安装）
curl -LsSf https://astral.sh/uv/install.sh | sh

# 创建虚拟环境并安装依赖
cd qa
uv sync
```

## seed_qa_users.py - 批量创建测试用户

用于快速创建指定数量的 QA 测试用户（普通用户和商家用户），所有用户均为已通过邮箱验证的激活状态。

### 前置条件

- PostgreSQL 服务已启动
- `backend/.env` 中存在正确的数据库连接配置

### 使用方法

```bash
# 进入 qa 目录
cd qa

# 激活虚拟环境
source .venv/bin/activate

# 创建默认各 5 个普通用户和商家用户
python seed_qa_users.py

# 指定数量
python seed_qa_users.py --personal 10 --merchant 3

# 仅创建商家用户
python seed_qa_users.py --personal 0 --merchant 5
```

### 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--personal N` | 创建的普通用户数量 | 5 |
| `--merchant M` | 创建的商家用户数量 | 5 |

### 输出示例

```
正在创建 5 个普通用户...
  普通用户: users 插入 5 条, personal_profiles 插入 5 条
正在创建 5 个商家用户...
  商家用户: users 插入 5 条, merchant_profiles 插入 5 条

普通用户:
  qa_personal_1@mayoistar.qa / xK9mP2qW7aB3
  qa_personal_2@mayoistar.qa / rJ5nQ8vT6cL4
  ...

商家用户:
  qa_merchant_1@mayoistar.qa / dF3xR7jN9pW5
  qa_merchant_2@mayoistar.qa / hM6kL2sB8yV1
  ...

完成。共处理 5 个普通用户, 5 个商家用户。
```

### 注意事项

- 密码使用 BCrypt(12) 哈希，与后端登记密码方式一致
- 若邮箱或昵称与已有数据冲突，对应记录将被跳过并输出警告
- 生成的密码仅在脚本输出中展示一次，请妥善保存
- 脚本运行前不会检查 PostgreSQL 连接，连接失败将直接报错退出
