# Gitee 协作说明

本目录提供 Windows PowerShell 脚本，辅助与 Gitee 远程仓库同步。

## 首次配置

```powershell
.\scripts\gitee\setup-remote.ps1 -RemoteUrl "https://gitee.com/你的用户名/hrmini.git"
```

## 日常命令

```powershell
# 拉取最新代码
.\scripts\gitee\pull.ps1

# 提交并推送（有未提交变更时会自动 git add + commit）
.\scripts\gitee\push.ps1 -Message "feat: xxx"
```

## 凭证

- HTTPS：在 Gitee 生成私人令牌，推送时作为密码
- SSH：将公钥添加到 Gitee，远程地址改为 `git@gitee.com:xxx/hrmini.git`

## 模板

Issue / PR 模板见项目根目录 `.gitee/`。
