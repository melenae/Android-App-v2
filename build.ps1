# Сборка проекта с подходящим JDK 17+
# Использует первый найденный JDK 17+ или текущий JAVA_HOME, если он корректен.

$possiblePaths = @(
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Eclipse Adoptium\jdk-17*",
    "C:\Program Files\Microsoft\jdk-17*",
    "C:\Program Files\Java\jdk-17*",
    "C:\Program Files\Java\jdk-21*",
    "C:\Program Files\BellSoft\LibericaJDK-17*",
    "C:\Program Files\BellSoft\LibericaJDK-21*",
    "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-17*",
    "$env:LOCALAPPDATA\Programs\Microsoft\jdk-17*"
)

$found = $null
foreach ($pattern in $possiblePaths) {
    $dir = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($dir -and (Test-Path (Join-Path $dir.FullName "bin\java.exe"))) {
        $found = $dir.FullName
        break
    }
}

if ($found) {
    $env:JAVA_HOME = $found
    Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    Write-Host "Using existing JAVA_HOME=$env:JAVA_HOME"
} else {
    Write-Host "JDK 17+ not found in standard locations. Install JDK 17 (e.g. from https://adoptium.net) or set JAVA_HOME manually."
    Write-Host "You can also build from Android Studio: Build -> Build Bundle(s) / APK(s) -> Build APK(s)"
    exit 1
}

& .\gradlew.bat @args
