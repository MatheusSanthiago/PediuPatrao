$ErrorActionPreference = 'Stop'
$raiz = 'C:\GitHub\PediuPatrao'
$trabalho = Join-Path $raiz '.local\mongodb-replica'
$config = 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.cfg'
$dados = 'C:\Program Files\MongoDB\Server\9.0\data'
$java = 'C:\Program Files\Java\jdk-25.0.4\bin\java.exe'
$libs = Join-Path $raiz 'target\ferramentas-banco\lib\*'
$helper = Join-Path $trabalho 'PrepararReplica.java'
$resultado = Join-Path $trabalho 'resultado.json'
$backup = Join-Path $trabalho ('backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$alterado = $false
$parado = $false
function Executar-Java([string[]]$Parametros) {
    $psi = New-Object Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = '--class-path "' + $libs + '" "' + $helper + '" ' + (($Parametros | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $processo = [Diagnostics.Process]::Start($psi)
    $saida = $processo.StandardOutput.ReadToEnd()
    $erro = $processo.StandardError.ReadToEnd()
    $processo.WaitForExit()
    Add-Content -LiteralPath (Join-Path $trabalho 'configuracao.log') -Value ($saida + $erro) -Encoding UTF8
    if ($processo.ExitCode -ne 0) { throw "Falha na etapa Java: $($Parametros[0]). Consulte configuracao.log." }
}
try {
    $identidade = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identidade)
    if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Execute como administrador do Windows.' }
    $conteudo = [IO.File]::ReadAllText($config)
    if ($conteudo -match '(?m)^replication\s*:') { throw 'Já existe configuração de replication; é necessário conferir antes de alterar.' }
    if ($conteudo -notmatch 'dbPath:\s*C:\\Program Files\\MongoDB\\Server\\9\.0\\data') { throw 'O caminho dos dados mudou.' }
    New-Item -ItemType Directory -Path $backup | Out-Null
    Copy-Item -LiteralPath $config -Destination (Join-Path $backup 'mongod.cfg')
    Executar-Java @('registrar', (Join-Path $backup 'referencia.json'))
    Stop-Service -Name MongoDB
    (Get-Service MongoDB).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
    $parado = $true
    # Cópia física com o banco parado: preserva todos os bancos, índices e credenciais.
    Copy-Item -LiteralPath $dados -Destination (Join-Path $backup 'data') -Recurse
    $originais = Get-ChildItem -LiteralPath $dados -File -Recurse
    foreach ($arquivo in $originais) {
        $relativo = $arquivo.FullName.Substring($dados.Length).TrimStart('\')
        $copia = Join-Path (Join-Path $backup 'data') $relativo
        if ((Get-FileHash -LiteralPath $arquivo.FullName).Hash -ne (Get-FileHash -LiteralPath $copia).Hash) {
            throw "Falha ao conferir a cópia de segurança: $relativo"
        }
    }
    $novo = $conteudo + "`r`nreplication:`r`n  replSetName: rs0`r`n  oplogSizeMB: 128`r`n"
    [IO.File]::WriteAllText($config, $novo, (New-Object Text.UTF8Encoding($false)))
    $alterado = $true
    Start-Service -Name MongoDB
    (Get-Service MongoDB).WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
    $parado = $false
    Executar-Java @('iniciar')
    Executar-Java @('conferir', (Join-Path $backup 'referencia.json'))
    @{sucesso=$true;backup=$backup;replicaSet='rs0';porta=27017} | ConvertTo-Json | Set-Content -LiteralPath $resultado -Encoding UTF8
} catch {
    $mensagem = $_.Exception.Message
    # Em caso de falha, restaura a configuração; os arquivos de dados não são substituídos.
    if ($alterado) {
        try {
            Stop-Service -Name MongoDB
            Copy-Item -LiteralPath (Join-Path $backup 'mongod.cfg') -Destination $config -Force
            Start-Service -Name MongoDB
        } catch { $mensagem += ' Falha ao restaurar serviço: ' + $_.Exception.Message }
    } elseif ($parado) {
        try { Start-Service -Name MongoDB } catch { $mensagem += ' Falha ao reiniciar serviço: ' + $_.Exception.Message }
    }
    @{sucesso=$false;erro=$mensagem;backup=$backup} | ConvertTo-Json | Set-Content -LiteralPath $resultado -Encoding UTF8
    exit 1
}