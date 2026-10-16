param(
  [ValidateSet('win-x64','win-arm64')][string]$Runtime = 'win-arm64',
  [string]$Configuration = 'Release'
)
$ErrorActionPreference = 'Stop'
$project = Join-Path $PSScriptRoot 'StreamX.Windows.csproj'
$out = Join-Path $PSScriptRoot "publish/$Runtime"
dotnet restore $project -r $Runtime
dotnet publish $project -c $Configuration -r $Runtime --self-contained true -p:PublishSingleFile=false -o $out
Write-Host "Published StreamX Windows v0.2 to $out"
