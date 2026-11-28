param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 5432,
    [string]$Database = "restaurant_pos",
    [string]$AppUser = "restaurant_pos"
)

$ErrorActionPreference = "Stop"

function Read-SecretText([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Assert-Identifier([string]$Value, [string]$Label) {
    if ($Value -notmatch '^[A-Za-z][A-Za-z0-9_]{0,62}$') {
        throw "$Label must start with a letter and contain only letters, digits, or underscores."
    }
}

Assert-Identifier $Database "Database"
Assert-Identifier $AppUser "Application user"

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found. Install PostgreSQL client tools and add its bin directory to PATH."
}

$adminUser = Read-Host "PostgreSQL administrator username (usually postgres)"
$adminPassword = Read-SecretText "PostgreSQL administrator password"
$appPassword = Read-SecretText "New application database password"
if ([string]::IsNullOrWhiteSpace($appPassword) -or $appPassword.Length -gt 256) {
    throw "Application database password must contain 1 to 256 characters."
}
$escapedPassword = $appPassword.Replace("'", "''")

try {
    $env:PGPASSWORD = $adminPassword
    $roleExists = & psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $adminUser -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$AppUser'"
    if ($LASTEXITCODE -ne 0) { throw "Could not query PostgreSQL roles." }

    if ($roleExists.Trim() -eq "1") {
        $sql = "ALTER ROLE `"$AppUser`" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$escapedPassword';"
    } else {
        $sql = "CREATE ROLE `"$AppUser`" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '$escapedPassword';"
    }
    & psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $adminUser -d postgres -c $sql
    if ($LASTEXITCODE -ne 0) { throw "Could not create or update the application database role." }

    $databaseExists = & psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $adminUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$Database'"
    if ($LASTEXITCODE -ne 0) { throw "Could not query PostgreSQL databases." }

    if ($databaseExists.Trim() -eq "1") {
        & psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $adminUser -d postgres -c "ALTER DATABASE `"$Database`" OWNER TO `"$AppUser`";"
    } else {
        & psql -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $adminUser -d postgres -c "CREATE DATABASE `"$Database`" OWNER `"$AppUser`";"
    }
    if ($LASTEXITCODE -ne 0) { throw "Could not create or assign the application database." }

    Write-Host "Database and non-superuser application role are ready."
    Write-Host "Enter host=$HostName port=$Port database=$Database username=$AppUser in Restaurant POS."
    Write-Host "The application stores its database password in the operating-system credential vault."
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $adminPassword = $null
    $appPassword = $null
    $escapedPassword = $null
}
