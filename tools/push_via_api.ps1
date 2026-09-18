# 推送脚本：当 github.com 被网络阻断、git push 无法使用时，改走 GitHub Git Data API。
#
# 用法（PowerShell）：
#   1) 设置令牌（GitHub Personal Access Token，需 repo 权限）：
#        $env:GH_TOKEN = "ghp_xxx"          # 仅当前会话有效，本脚本不会写入任何文件
#   2) 在仓库内先完成本地提交：git add -A; git commit -m "..."
#   3) 运行：powershell -ExecutionPolicy Bypass -File tools\push_via_api.ps1
#
# 原理：POST /git/blobs（逐个上传变更文件）→ POST /git/trees → POST /git/commits → PATCH /git/refs/heads/main
# 关键点：
#   - 上传的是「提交内的字节」（git cat-file blob），不是工作区文件；否则换行符差异会让 tree 校验失败并中止
#   - 新提交的 parent 取远端当前 tip ⇒ 快进更新，不需要强推
#   - 提交前强校验：API 返回的 tree sha 必须等于 git rev-parse HEAD^{tree}
#   - 远端 tip 若由本脚本创建，则本地没有该对象；此时以「tree sha 相同的本地提交」作为 diff 基线
#
# 注意：本脚本默认推送 remote 名为 under 的仓库（LingRonghui/Under-control），owner/repo 见下方常量。

$ErrorActionPreference = "Stop"
$tok = $env:GH_TOKEN
if (-not $tok) { throw "GH_TOKEN is missing (set $env:GH_TOKEN first)" }

$owner = "LingRonghui"
$repo  = "Under-control"
$branch = "main"

# 脚本位于 <repo>/tools/ 下，仓库根即其上级目录（便于换机后直接使用）
$root = Split-Path -Parent $PSScriptRoot
$api  = "https://api.github.com/repos/$owner/$repo"
$tmp  = Join-Path ([System.IO.Path]::GetTempPath()) "trae_git_blob.bin"

$h = @{
    Authorization          = "Bearer $tok"
    Accept                 = "application/vnd.github+json"
    "User-Agent"           = "trae-api-push"
    "X-GitHub-Api-Version" = "2022-11-28"
}

function Api($method, $uri, $body) {
    $p = @{ Method = $method; Uri = $uri; Headers = $h; TimeoutSec = 900 }
    if ($body) { $p.Body = $body; $p.ContentType = "application/json; charset=utf-8" }
    Invoke-RestMethod @p
}

$me = Api "GET" "https://api.github.com/user"
Write-Output "auth user: $($me.login)"

$ref = Api "GET" "$api/git/ref/heads/$branch"
$remoteSha = $ref.object.sha
Write-Output "remote $branch`: $remoteSha"

$remoteTree = (Api "GET" "$api/git/commits/$remoteSha").tree.sha
Write-Output "remote tree: $remoteTree"

# 找到「tree 与远端一致」的本地提交，作为 diff 基线（远端 tip 可能是本脚本创建的、本地并不存在）
$base = $null
foreach ($line in (git -C $root log --format="%H|%T" -n 30)) {
    $p = $line -split "\|"
    if ($p[1] -eq $remoteTree) { $base = $p[0]; break }
}
if (-not $base) { throw "cannot locate local base commit for remote tree $remoteTree" }
Write-Output "base local commit: $base"

$entries = @()
foreach ($line in (git -C $root ls-tree -r HEAD)) {
    $split = $line -split "`t", 2
    if ($split.Count -lt 2) { continue }
    $m = ($split[0]) -split " +"
    $entries += @{ mode = $m[0]; sha = $m[2]; path = $split[1] }
}
Write-Output "HEAD entries: $($entries.Count)"

$changed = @()
foreach ($l in (git -C $root diff --name-status $base HEAD)) {
    $parts = $l -split "`t"
    if ($parts.Count -lt 2) { continue }
    if ($parts[0] -eq "A" -or $parts[0] -eq "M") { $changed += $parts[1] }
}
Write-Output "changed files: $($changed.Count)"

$failed = 0
foreach ($p in $changed) {
    $e = $entries | Where-Object { $_.path -eq $p } | Select-Object -First 1
    if (-not $e) { Write-Output "SKIP(no entry): $p"; continue }
    $cmd = 'git -C "' + $root + '" cat-file blob ' + $e.sha + ' > "' + $tmp + '"'
    cmd.exe /c $cmd | Out-Null
    $bytes = [System.IO.File]::ReadAllBytes($tmp)
    $payload = @{ content = [Convert]::ToBase64String($bytes); encoding = "base64" } | ConvertTo-Json -Compress
    $r = Api "POST" "$api/git/blobs" $payload
    if ($r.sha -ne $e.sha) { $failed++ }
    Write-Output ("blob {0} ({1} bytes) {2}" -f $p, $bytes.Length, $r.sha.Substring(0, 8))
}
if ($failed -gt 0) { Write-Output "BLOB SHA MISMATCH x$failed -> abort"; exit 3 }

$tree = @()
foreach ($e in $entries) { $tree += @{ path = $e.path; mode = $e.mode; type = "blob"; sha = $e.sha } }
$treeResp = Api "POST" "$api/git/trees" (@{ tree = $tree } | ConvertTo-Json -Depth 6 -Compress)
$newTreeSha = $treeResp.sha
$localTreeSha = (git -C $root rev-parse "HEAD^{tree}").Trim()
Write-Output "tree local=$localTreeSha api=$newTreeSha match=$($localTreeSha -eq $newTreeSha)"
if ($localTreeSha -ne $newTreeSha) { Write-Output "TREE MISMATCH -> remote ref NOT updated"; exit 2 }

$msg = ((git -C $root log -1 --format=%B HEAD) -join "`n").TrimEnd()
$commit = Api "POST" "$api/git/commits" (@{ message = $msg; tree = $newTreeSha; parents = @($remoteSha) } | ConvertTo-Json -Compress)
Write-Output "new commit: $($commit.sha)"

Api "PATCH" "$api/git/refs/heads/$branch" (@{ sha = $commit.sha; force = $false } | ConvertTo-Json -Compress) | Out-Null

$after = Api "GET" "$api/git/ref/heads/$branch"
Write-Output "remote $branch now: $($after.object.sha)"
Write-Output "done"
