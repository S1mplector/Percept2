# WiX Toolset Installation Script
# Downloads and installs WiX Toolset for creating Windows installers

Write-Host "=== WiX Toolset Installer ===" -ForegroundColor Cyan

# Check if running as administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")
if (-not $isAdmin) {
    Write-Host "WARNING: Not running as administrator. Installation may require admin rights." -ForegroundColor Yellow
}

# WiX download URL (version 3.14 - stable)
$wixUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314.exe"
$downloadPath = "$env:TEMP\wix314.exe"

Write-Host "`nDownloading WiX Toolset..." -ForegroundColor Yellow
Write-Host "URL: $wixUrl" -ForegroundColor Gray

try {
    # Download WiX installer
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $wixUrl -OutFile $downloadPath -UseBasicParsing
    $ProgressPreference = 'Continue'
    
    Write-Host "Download completed!" -ForegroundColor Green
    Write-Host "Downloaded to: $downloadPath" -ForegroundColor Gray
    
    # Install WiX
    Write-Host "`nInstalling WiX Toolset..." -ForegroundColor Yellow
    Write-Host "This will open the WiX installer. Please follow the installation wizard." -ForegroundColor Cyan
    
    Start-Process -FilePath $downloadPath -Wait
    
    # Check if WiX was installed successfully
    Write-Host "`nChecking WiX installation..." -ForegroundColor Yellow
    
    # Common WiX installation paths
    $wixPaths = @(
        "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin",
        "${env:ProgramFiles}\WiX Toolset v3.14\bin",
        "${env:ProgramFiles(x86)}\WiX Toolset v3.11\bin",
        "${env:ProgramFiles}\WiX Toolset v3.11\bin"
    )
    
    $wixFound = $false
    foreach ($path in $wixPaths) {
        if (Test-Path "$path\candle.exe") {
            $wixFound = $true
            Write-Host "WiX found at: $path" -ForegroundColor Green
            
            # Add to PATH if not already there
            $currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
            if ($currentPath -notlike "*$path*") {
                Write-Host "Adding WiX to PATH..." -ForegroundColor Yellow
                [Environment]::SetEnvironmentVariable("Path", "$currentPath;$path", "User")
                Write-Host "WiX added to PATH. Please restart your terminal for changes to take effect." -ForegroundColor Cyan
            } else {
                Write-Host "WiX is already in PATH." -ForegroundColor Green
            }
            break
        }
    }
    
    if (-not $wixFound) {
        Write-Host "WARNING: WiX installation not found in expected locations." -ForegroundColor Yellow
        Write-Host "Please ensure WiX was installed and add its 'bin' directory to your PATH manually." -ForegroundColor Yellow
        Write-Host "Typical location: C:\Program Files (x86)\WiX Toolset v3.14\bin" -ForegroundColor Gray
    } else {
        Write-Host "`n=== WiX Toolset installed successfully! ===" -ForegroundColor Green
        Write-Host "You can now create .exe and .msi installers using create-installer.bat" -ForegroundColor Cyan
    }
    
    # Clean up
    Remove-Item $downloadPath -Force -ErrorAction SilentlyContinue
    
} catch {
    Write-Host "ERROR: Failed to download or install WiX Toolset" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "`nYou can manually download WiX from:" -ForegroundColor Yellow
    Write-Host "https://wixtoolset.org/releases/" -ForegroundColor Cyan
}