param(
    [ValidateSet('Todo','Backend','Frontend','Correo')]
    [string]$Servicio = 'Todo'
)
$ErrorActionPreference = 'Stop'
switch ($Servicio) {
    'Backend' {
$ErrorActionPreference='Stop'
Set-Location (Join-Path $PSScriptRoot 'NEXUM-backend')
# Usar JAVA_HOME o localizar un JDK 21 instalado.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    $candidatos=@()
    foreach ($raiz in @('C:/Program Files/Java','C:/Program Files/Eclipse Adoptium','C:/Program Files/Microsoft')) {
        if (Test-Path $raiz) { $candidatos += Get-ChildItem -LiteralPath $raiz -Directory | Where-Object { $_.Name -like '*21*' -and (Test-Path (Join-Path $_.FullName 'bin/javac.exe')) } }
    }
    $jdk=$candidatos | Select-Object -First 1
    if (-not $jdk) { throw 'Instala Java JDK 21 o configura JAVA_HOME antes de continuar.' }
    $env:JAVA_HOME=$jdk.FullName
}
if (Get-NetTCPConnection -LocalPort 8086 -State Listen -ErrorAction SilentlyContinue) {
    throw 'El puerto 8086 esta ocupado. Deten el backend anterior con Ctrl+C antes de iniciar otro.'
}
& .\mvnw.cmd spring-boot:run
if ($LASTEXITCODE -ne 0) { throw 'El backend no pudo iniciar. Revisa el error anterior.' }

        return
    }
    'Frontend' {
$ErrorActionPreference='Stop'
Set-Location (Join-Path $PSScriptRoot 'NEXUM-frontend')
if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Instala Node.js con npm antes de continuar.' }
if (-not (Test-Path 'node_modules')) {
    npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias del frontend.' }
}
npm.cmd start -- --host 127.0.0.1 --port 4306

        return
    }
    'Correo' {
$ErrorActionPreference = 'Stop'
function ConvertTo-PropertyValue([string]$value) {
    return $value.Replace('\', '\\').Replace("`r", '\r').Replace("`n", '\n').Replace("`t", '\t').Replace(' ', '\ ').Replace(':', '\:').Replace('=', '\=').Replace('#', '\#').Replace('!', '\!')
}
$secret = $null
try {
    $gmail = (Read-Host 'Tu Gmail').Trim().ToLowerInvariant()
    if ($gmail -notmatch '^[^\s@]+@gmail\.com$') { throw 'Introduce una direccion de Gmail valida.' }
    $secret = Read-Host 'Contrasena de aplicacion de Google (entrada oculta)' -AsSecureString
    $password = [System.Net.NetworkCredential]::new('', $secret).Password -replace '\s', ''
    if ($password -notmatch '^[A-Za-z]{16}$') { throw 'Se requieren los 16 caracteres de la contrasena de aplicacion.' }
    $path = Join-Path $PSScriptRoot 'NEXUM-backend/.local/application-local.properties'
    if (-not (Test-Path -LiteralPath $path)) { throw 'Falta .local/application-local.properties.' }
    $existing = [System.IO.File]::ReadAllLines($path) | Where-Object { $_ -notmatch '^\s*(spring\.mail\.|app\.mail\.from\s*[=:])' }
    $settings = @(
        'spring.mail.host=smtp.gmail.com',
        'spring.mail.port=587',
        ('spring.mail.username=' + (ConvertTo-PropertyValue $gmail)),
        ('spring.mail.password=' + (ConvertTo-PropertyValue $password)),
        'spring.mail.properties.mail.smtp.auth=true',
        'spring.mail.properties.mail.smtp.starttls.enable=true',
        'spring.mail.properties.mail.smtp.starttls.required=true',
        ('app.mail.from=' + (ConvertTo-PropertyValue $gmail))
    )
    [System.IO.File]::WriteAllLines($path, [string[]](@($existing) + $settings), [System.Text.UTF8Encoding]::new($false))
    Write-Host 'Configuracion guardada en .local/application-local.properties. Reinicia el backend para aplicarla.'
} finally {
    $password = $null
    $settings = $null
    if ($secret) { $secret.Dispose() }
}

        return
    }
}
# Cada servicio usa este mismo archivo. Solo se detienen los procesos creados aqui.
foreach ($port in @(8086,4306)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "El puerto $port esta ocupado. Deten la ejecucion anterior con Ctrl+C."
    }
}
$logs = Join-Path $PSScriptRoot 'NEXUM-backend/.local'
$processes = @()
try {
    foreach ($service in @('Backend','Frontend')) {
        $arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $PSCommandPath + '" -Servicio ' + $service
        $processes += Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logs ($service + '.log')) -RedirectStandardError (Join-Path $logs ($service + '.error.log'))
    }
    Write-Host 'Iniciando NEXUM. Abre http://127.0.0.1:4306 cuando ambos servicios esten listos.'
    Write-Host 'Pulsa Ctrl+C en esta terminal para detener ambos servicios.'
    Write-Host "Registros de arranque: $logs"
    while ($true) {
        foreach ($process in $processes) {
            $process.Refresh()
            if ($process.HasExited) {
                foreach ($file in Get-ChildItem -LiteralPath $logs -Filter '*.error.log') { Get-Content -LiteralPath $file.FullName -Tail 12 }
                throw 'Uno de los servicios termino. Revisa Backend.log y Frontend.log en la carpeta .local.'
            }
        }
        Start-Sleep -Seconds 1
    }
} finally {
    foreach ($process in $processes) {
        if (-not $process.HasExited) { & taskkill.exe /PID $process.Id /T /F | Out-Null }
        $process.Dispose()
    }
}
