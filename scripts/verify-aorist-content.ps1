param([ValidateRange(1024,65535)][int]$Port = 18089)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$aoristRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$aoristJar = Join-Path $aoristRoot 'target/bulgariantrainer-0.0.1-SNAPSHOT.jar'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21 and build with the Maven Wrapper first.' }
$aoristJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (-not (Test-Path -LiteralPath $aoristJar)) { throw 'Run .\mvnw.cmd clean verify first.' }
$aoristVersion = (& $aoristJava -version 2>&1 | Out-String)
if ($aoristVersion -notmatch 'version "21\.') { throw 'JAVA_HOME must point to JDK 21.' }
if ($Port -eq 8080) { throw 'Use a separate test port, not the normal application port.' }
$aoristProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$Port)
try { $aoristProbe.Start() } finally { $aoristProbe.Stop() }
$aoristRun = 'aorist-http-' + [Guid]::NewGuid().ToString('N')
$aoristDirectory = Join-Path $aoristRoot ('target/' + $aoristRun)
New-Item -ItemType Directory -Path $aoristDirectory | Out-Null
$aoristBase = 'http://127.0.0.1:' + $Port
$aoristUrl = 'jdbc:h2:file:./target/' + $aoristRun + '/database;WRITE_DELAY=0'
$aoristProcess = $null
$aoristOldBase = $env:AORIST_HTTP_BASE_URL
$aoristOldRestart = $env:AORIST_HTTP_RESTART
function Start-AoristTestServer([string]$phase) {
    $aoristArguments = @('-jar',('"{0}"' -f $aoristJar),'--server.address=127.0.0.1',('--server.port=' + $Port),('"--spring.datasource.url={0}"' -f $aoristUrl))
    $script:aoristProcess = Start-Process -FilePath $aoristJava -ArgumentList $aoristArguments -WorkingDirectory $aoristRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $aoristDirectory ($phase + '.out.log')) -RedirectStandardError (Join-Path $aoristDirectory ($phase + '.err.log'))
    for ($aoristPoll=0; $aoristPoll -lt 240; $aoristPoll++) {
        if ($script:aoristProcess.HasExited) { throw "Test server exited; inspect $aoristDirectory" }
        try { if ((Invoke-RestMethod ($aoristBase + '/api/health') -TimeoutSec 1).status -eq 'UP') { return } } catch { }
        Start-Sleep -Milliseconds 250
    }
    throw "Test server did not become ready; inspect $aoristDirectory"
}
function Stop-AoristTestServer {
    if ($null -ne $script:aoristProcess -and -not $script:aoristProcess.HasExited) {
        Stop-Process -Id $script:aoristProcess.Id -Force
        $script:aoristProcess.WaitForExit()
    }
    $script:aoristProcess = $null
}
Push-Location $aoristRoot
try {
    $env:AORIST_HTTP_BASE_URL = $aoristBase
    $env:AORIST_HTTP_RESTART = '0'
    Start-AoristTestServer 'initial'
    node --test src/test/js/aorist-content-http.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Initial HTTP checks failed.' }
    Stop-AoristTestServer
    $env:AORIST_HTTP_RESTART = '1'
    Start-AoristTestServer 'restart'
    node --test src/test/js/aorist-content-http.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Restart HTTP checks failed.' }
    Write-Output "PASS: isolated HTTP and JVM restart checks. Database and server logs: $aoristDirectory"
} finally {
    Stop-AoristTestServer
    $env:AORIST_HTTP_BASE_URL = $aoristOldBase
    $env:AORIST_HTTP_RESTART = $aoristOldRestart
    Pop-Location
}
