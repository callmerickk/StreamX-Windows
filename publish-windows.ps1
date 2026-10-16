$ErrorActionPreference = 'Stop'
dotnet restore .\StreamX.Windows.csproj
dotnet publish .\StreamX.Windows.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=false -o .\publish\win-x64
dotnet publish .\StreamX.Windows.csproj -c Release -r win-arm64 --self-contained true -p:PublishSingleFile=false -o .\publish\win-arm64
Write-Host 'Publish complete. Test each output on its matching Windows architecture.'
