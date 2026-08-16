import argparse
import base64
import hashlib
import os
import sys

import paramiko


class ConfirmHostKeyPolicy(paramiko.MissingHostKeyPolicy):
    def missing_host_key(self, client, hostname, key):
        fingerprint = base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip("=")
        print(f"首次连接服务器 {hostname}，SSH 主机指纹为 SHA256:{fingerprint}")
        answer = input("确认这是正确的服务器请输入 YES：").strip()
        if answer != "YES":
            raise paramiko.SSHException("未确认服务器主机指纹")
        client.get_host_keys().add(hostname, key.get_name(), key)
        host_keys_file = os.path.join(os.path.dirname(__file__), "..", ".local", "deploy", "known_hosts")
        client.save_host_keys(os.path.abspath(host_keys_file))


def main():
    parser = argparse.ArgumentParser(description="Deploy PMS artifacts to the production Windows server")
    parser.add_argument("--host", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--frontend", required=True)
    parser.add_argument("--backend", required=True)
    parser.add_argument("--release-id", required=True)
    args = parser.parse_args()

    password = os.environ.get("PMS_DEPLOY_PASSWORD")
    if not password:
        raise RuntimeError("没有获取到服务器密码")

    state_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".local", "deploy"))
    known_hosts = os.path.join(state_dir, "known_hosts")
    remote_frontend = f"C:/Windows/Temp/pms-frontend-{args.release_id}.zip"
    remote_backend = f"C:/Windows/Temp/pms-backend-{args.release_id}.jar"
    remote_deploy_script = f"C:/Windows/Temp/pms-deploy-{args.release_id}.ps1"

    client = paramiko.SSHClient()
    if os.path.exists(known_hosts):
        client.load_host_keys(known_hosts)
    client.set_missing_host_key_policy(ConfirmHostKeyPolicy())

    print("正在连接生产服务器……")
    client.connect(
        args.host,
        username=args.username,
        password=password,
        timeout=20,
        auth_timeout=20,
        banner_timeout=20,
        look_for_keys=False,
        allow_agent=False,
    )

    try:
        with client.open_sftp() as sftp:
            print("上传前端压缩包……")
            sftp.put(args.frontend, remote_frontend)
            print("上传后端 JAR……")
            sftp.put(args.backend, remote_backend)

        remote_script = r'''
$ErrorActionPreference = 'Stop'
$releaseId = '__RELEASE_ID__'
$frontendArchive = 'C:\Windows\Temp\pms-frontend-__RELEASE_ID__.zip'
$backendUpload = 'C:\Windows\Temp\pms-backend-__RELEASE_ID__.jar'
$frontendPath = 'C:\nginx\html\pms'
$backendPath = 'C:\Projects\pms\backend\pms-backend-1.0.0.jar'
$backupRoot = "C:\Projects\pms\deploy-backups\$releaseId"
$frontendBackup = Join-Path $backupRoot 'frontend'
$backendBackup = Join-Path $backupRoot 'pms-backend-1.0.0.jar'
$frontendStage = "C:\Windows\Temp\pms-frontend-$releaseId"
$backendWasStopped = $false
$frontendWasBackedUp = $false

try {
    New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
    if (Test-Path -LiteralPath $frontendStage) {
        Remove-Item -LiteralPath $frontendStage -Recurse -Force
    }
    New-Item -ItemType Directory -Path $frontendStage -Force | Out-Null
    Expand-Archive -LiteralPath $frontendArchive -DestinationPath $frontendStage -Force
    if (-not (Test-Path -LiteralPath (Join-Path $frontendStage 'index.html'))) {
        throw '上传的前端压缩包中没有 index.html'
    }

    if (Test-Path -LiteralPath $backendPath) {
        Copy-Item -LiteralPath $backendPath -Destination $backendBackup -Force
    }
    if (Test-Path -LiteralPath $frontendPath) {
        New-Item -ItemType Directory -Path $frontendBackup -Force | Out-Null
        Copy-Item -Path (Join-Path $frontendPath '*') -Destination $frontendBackup -Recurse -Force
        $frontendWasBackedUp = $true
    }

    & cmd.exe /d /c 'nssm stop PMS_BACKEND >nul 2>&1'
    $backendWasStopped = $true

    Copy-Item -LiteralPath $backendUpload -Destination $backendPath -Force
    if (Test-Path -LiteralPath $frontendPath) {
        Remove-Item -LiteralPath $frontendPath -Recurse -Force
    }
    New-Item -ItemType Directory -Path $frontendPath -Force | Out-Null
    Copy-Item -Path (Join-Path $frontendStage '*') -Destination $frontendPath -Recurse -Force

    & cmd.exe /d /c 'nssm start PMS_BACKEND >nul 2>&1'
    if ($LASTEXITCODE -ne 0) { throw "启动 PMS_BACKEND 失败：$LASTEXITCODE" }
    $backendWasStopped = $false

    Push-Location 'C:\nginx'
    try {
        & .\nginx.exe -s reload
        if ($LASTEXITCODE -ne 0) { throw "Nginx 重载失败：$LASTEXITCODE" }
    }
    finally { Pop-Location }

    $healthy = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        Start-Sleep -Seconds 2
        try {
            $result = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/device/latest' -UseBasicParsing -TimeoutSec 5
            if ($result.StatusCode -eq 200) { $healthy = $true; break }
        }
        catch { Write-Host "等待后端启动（$attempt/30）……" }
    }
    if (-not $healthy) { throw '后端在 60 秒内未通过健康检查' }

    Write-Host "RELEASE_OK:$releaseId"
}
catch {
    $failure = $_
    Write-Host "发布失败，开始回滚：$failure" -ForegroundColor Red
    & cmd.exe /d /c 'nssm stop PMS_BACKEND >nul 2>&1'

    if (Test-Path -LiteralPath $backendBackup) {
        Copy-Item -LiteralPath $backendBackup -Destination $backendPath -Force
    }
    if ($frontendWasBackedUp -and (Test-Path -LiteralPath $frontendBackup)) {
        if (Test-Path -LiteralPath $frontendPath) {
            Remove-Item -LiteralPath $frontendPath -Recurse -Force
        }
        New-Item -ItemType Directory -Path $frontendPath -Force | Out-Null
        Copy-Item -Path (Join-Path $frontendBackup '*') -Destination $frontendPath -Recurse -Force
    }
    & cmd.exe /d /c 'nssm start PMS_BACKEND >nul 2>&1'
    Push-Location 'C:\nginx'
    try { & .\nginx.exe -s reload 2>$null | Out-Null } finally { Pop-Location }
    throw $failure
}
finally {
    Remove-Item -LiteralPath $frontendArchive -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $backendUpload -Force -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $frontendStage) {
        Remove-Item -LiteralPath $frontendStage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
'''.replace("__RELEASE_ID__", args.release_id)

        # Uploading the script avoids Windows' 8191-character command-line limit.
        with client.open_sftp() as sftp:
            with sftp.file(remote_deploy_script, "wb") as script_file:
                script_file.write(b"\xff\xfe" + remote_script.encode("utf-16-le"))

        try:
            print("服务器正在备份并更新服务……")
            remote_script_windows = remote_deploy_script.replace("/", "\\")
            command = (
                'powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass '
                f'-File "{remote_script_windows}"'
            )
            stdin, stdout, stderr = client.exec_command(command, timeout=180)
            stdout.channel.set_combine_stderr(True)
            # Windows OpenSSH returns PowerShell output using the server's OEM
            # code page (GBK on this host), while Paramiko text mode assumes UTF-8.
            stdout._flags |= stdout.FLAG_BINARY
            for raw_line in iter(stdout.readline, b""):
                line = raw_line.decode("gb18030", errors="replace").rstrip()
                if line:
                    print(line)
            exit_code = stdout.channel.recv_exit_status()
            if exit_code != 0:
                raise RuntimeError(f"服务器发布脚本执行失败，退出码：{exit_code}")
        finally:
            try:
                with client.open_sftp() as sftp:
                    sftp.remove(remote_deploy_script)
            except IOError:
                pass
    finally:
        client.close()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"发布失败：{exc}", file=sys.stderr)
        sys.exit(1)
