param([int]$Port=18087)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
$script:aspectVerificationProcess=$null
$script:aspectVerificationLaunch=0
try {
    if([string]::IsNullOrWhiteSpace($env:JAVA_HOME)){throw 'Set JAVA_HOME to a JDK 21 installation.'}
    $javaExecutable=Join-Path $env:JAVA_HOME 'bin/java.exe'
    $javaVersion=(& $javaExecutable --version | Select-Object -First 1)
    if($javaVersion -notmatch '^(openjdk|java) 21[.\s]'){throw "JDK 21 is required, found: $javaVersion"}
    if(-not (Test-Path 'target/bulgariantrainer-0.0.1-SNAPSHOT.jar')){throw 'Run .\mvnw.cmd clean verify first.'}
    if($Port -eq 8080){throw 'Choose a separate verification port, not the default personal-app port.'}
    # Fail before launching if another application owns the requested port.
    $portProbe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$Port)
    try{$portProbe.Start()}finally{$portProbe.Stop()}
    $smokeDirectory='target/aspect-verification-'+[guid]::NewGuid().ToString('N')
    New-Item -ItemType Directory -Path $smokeDirectory|Out-Null
    $datasource='jdbc:h2:file:./'+$smokeDirectory+'/database;WRITE_DELAY=0'
    $env:ASPECT_BROWSER_BASE_URL='http://127.0.0.1:'+$Port
    function Start-VerificationServer {
        $script:aspectVerificationLaunch++
        $script:aspectVerificationProcess=Start-Process -FilePath $javaExecutable -ArgumentList @('-jar','target/bulgariantrainer-0.0.1-SNAPSHOT.jar',"--server.port=$Port",("--spring.datasource.url="+$datasource)) -WindowStyle Hidden -PassThru -RedirectStandardOutput ($smokeDirectory+"/server-$script:aspectVerificationLaunch.log") -RedirectStandardError ($smokeDirectory+"/error-$script:aspectVerificationLaunch.log")
        for($attempt=0;$attempt -lt 60;$attempt++) {
            if($script:aspectVerificationProcess.HasExited){throw 'Verification application exited; inspect its logs.'}
            try{if((Invoke-RestMethod ($env:ASPECT_BROWSER_BASE_URL+'/api/health') -TimeoutSec 2).status -eq 'UP'){return}}catch{}
            Start-Sleep -Milliseconds 500
        }
        throw 'Verification application did not become ready.'
    }
    function Stop-VerificationServer {
        if($script:aspectVerificationProcess -and -not $script:aspectVerificationProcess.HasExited){Stop-Process -Id $script:aspectVerificationProcess.Id;$script:aspectVerificationProcess.WaitForExit()}
    }
    Start-VerificationServer
    & node --test src/test/js/aspect-pairs-browser.test.mjs > target/aspect-browser-results.log 2>&1
    $browserExit=$LASTEXITCODE;Get-Content target/aspect-browser-results.log
    if($browserExit -ne 0){throw 'Browser verification failed.'}
    Stop-VerificationServer
    Start-VerificationServer
    & node src/test/js/aspect-pairs-restart-check.mjs > target/aspect-restart-results.log 2>&1
    $restartExit=$LASTEXITCODE;Get-Content target/aspect-restart-results.log
    if($restartExit -ne 0){throw 'Process-restart verification failed.'}
    Write-Output ("PASS: disposable database "+$datasource)
} finally {
    if($script:aspectVerificationProcess -and -not $script:aspectVerificationProcess.HasExited){Stop-Process -Id $script:aspectVerificationProcess.Id;$script:aspectVerificationProcess.WaitForExit()}
    Pop-Location
}
