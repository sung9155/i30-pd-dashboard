# Builds the signed APKs of this checkout in the build container (docker/Dockerfile). The image is built once and
# reused; the signed APKs land in build\apk\.
#
#   .\docker\build.ps1                                # host tests, dashboard + My Apps
#   .\docker\build.ps1 dashboard --no-tests
#   .\docker\build.ps1 dashboard --version-name v1.17.13
#   .\docker\build.ps1 -Rebuild                       # build the image again (new tool versions in the Dockerfile)
#   .\docker\build.ps1 -Save i30pd-build.tar          # the image as a file (docker load -i brings it back, offline)
param(
  [switch]$Rebuild,
  [string]$Save,
  [Parameter(ValueFromRemainingArguments = $true)][string[]]$BuildArgs
)
$image = 'i30pd-build:latest'
$repo = Split-Path -Parent $PSScriptRoot

if ($Rebuild -or -not (docker images -q $image)) {
  docker build -t $image $PSScriptRoot
  if ($LASTEXITCODE) { exit $LASTEXITCODE }
}
if ($Save) {
  docker save -o $Save $image
  exit $LASTEXITCODE
}
docker run --rm -v "${repo}:/src" $image @BuildArgs
exit $LASTEXITCODE
