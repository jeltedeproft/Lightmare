# publish_to_itch.ps1
# Builds the GWT/HTML5 distribution and pushes it to the Lightmare itch.io page
# using Butler. Auto-increments the patch version in gradle.properties.
#
# Lightmare's itch page is currently private (restricted/draft) — that does not
# affect Butler. Butler authenticates with your account and can push to any of
# your projects regardless of public visibility. Just make sure on the itch page
# itself that "This file will be played in the browser" is checked, otherwise
# itch won't embed the upload as a playable HTML5 game.

$ErrorActionPreference = "Continue"

# Determine the project root
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Split-Path -Parent $scriptDir
Push-Location $projectRoot

# --- VERSION INCREMENT LOGIC ---
# Lightmare keeps its version in gradle.properties (projectVersion=X.Y.Z), not
# build.gradle. Patch-bump on every publish.
$propsPath = Join-Path $projectRoot "gradle.properties"
$content = Get-Content $propsPath -Raw

if ($content -match "projectVersion\s*=\s*(\d+\.\d+\.\d+)") {
    $currentVersion = $Matches[1]
    Write-Host "[VERSION] Current version found: $currentVersion" -ForegroundColor Cyan

    $parts = $currentVersion.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    $patch = [int]$parts[2]

    $patch++
    $newVersion = "$major.$minor.$patch"
    Write-Host "[VERSION] Incrementing to: $newVersion" -ForegroundColor Green

    $newContent = $content -replace "projectVersion\s*=\s*\d+\.\d+\.\d+", "projectVersion=$newVersion"
    Set-Content -Path $propsPath -Value $newContent -NoNewline

    $version = $newVersion
} else {
    Write-Warning "[WARN] Could not find projectVersion pattern in gradle.properties. Falling back to 1.0.0"
    $version = "1.0.0"
}
# -------------------------------

Write-Host "[INFO] Starting Lightmare Release & Publish Workflow (v$version)..." -ForegroundColor Cyan

# 1. Build the GWT/HTML5 distribution.
# `html:dist` runs the GWT compile and assembles html/build/dist/ with index.html
# and the JS bundle. We also run html:clean first because the dist task itself
# does not clean leftover JS files from previous builds.
Write-Host "[BUILD] Building HTML5 distribution..." -ForegroundColor Yellow
.\gradlew.bat html:clean html:dist --no-daemon

$distDir = "html/build/dist"

if (-not (Test-Path $distDir)) {
    Write-Error "[ERROR] Build output not found at $distDir. Aborting publish."
    Pop-Location
    exit 1
}

# 2. Push to itch.io.
# Channel name is arbitrary on itch; "html" is the convention for HTML5 builds.
# Project slug is jeltedeproft/lightmare (matches the page URL).
Write-Host "[PUSH] Pushing $distDir to Itch.io (jeltedeproft/lightmare:html)..." -ForegroundColor Green
$butlerPath = (Resolve-Path $distDir).Path.Replace('\', '/')
butler push "$butlerPath" "jeltedeproft/lightmare:html" --userversion $version

# 3. Notify Discord (optional).
# Lightmare's circle is currently you + one collaborator, so a webhook is
# probably overkill — left empty so this step is skipped. If you ever want
# notifications, paste a Discord webhook URL below.
$webhookUrl = ""
$logoUrl = ""

if ($webhookUrl -and $webhookUrl -ne "PASTE_YOUR_WEBHOOK_URL_HERE") {
    Write-Host "[NOTIFY] Sending announcement to Discord..." -ForegroundColor Yellow

    $payload = @{
        username = "Lightmare Publisher"
        embeds = @(
            @{
                title = "New Build Pushed: v$version"
                description = "Lightmare **v$version** is available on itch.io."
                url = "https://jeltedeproft.itch.io/lightmare"
                color = 2527403
                fields = @(
                    @{
                        name = "Itch page"
                        value = "[jeltedeproft.itch.io/lightmare](https://jeltedeproft.itch.io/lightmare)"
                        inline = $false
                    }
                )
                footer = @{
                    text = "Lightmare Automated Build System"
                }
            }
        )
    }

    if ($logoUrl) {
        $payload.embeds[0].thumbnail = @{ url = $logoUrl }
    }

    $payloadJson = $payload | ConvertTo-Json -Depth 10

    try {
        Invoke-RestMethod -Uri $webhookUrl -Method Post -Body ([System.Text.Encoding]::UTF8.GetBytes($payloadJson)) -ContentType "application/json"
        Write-Host "[SUCCESS] Discord notified!" -ForegroundColor Green
    } catch {
        Write-Warning "[WARN] Failed to notify Discord: $_"
    }
} else {
    Write-Host "[INFO] Discord webhook not set, skipping notification." -ForegroundColor DarkGray
}

Pop-Location
Write-Host "[SUCCESS] Publish process completed for v$version." -ForegroundColor Cyan
