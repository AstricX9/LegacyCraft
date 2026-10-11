#!/bin/sh
# LegacyCraft build console: builds ready-to-copy packages for each OS (see "packages" in build.gradle).
#   ./build.sh                 the menu
#   ./build.sh linuxX64 [ver]  one package straight away (windows, windowsX64, windowsX86, windowsArm64,
#                              linuxX64, linuxArm64, macosX64, macosArm64 or all), optionally setting the version
cd "$(dirname "$0")" || exit 1

if [ -n "$1" ]; then
    task="package$(printf '%s' "$1" | cut -c1 | tr '[:lower:]' '[:upper:]')$(printf '%s' "$1" | cut -c2-)"
    exec sh ./gradlew "$task" ${2:+"-PlcVersion=$2"}
fi

lc_version() { sed -n 's/^[Ll][Cc]:[[:space:]]*//p' version.txt | tr -d '\r' | head -n 1; }

package() {
    echo
    echo "  Building LegacyCraft $(lc_version) for $2..."
    echo
    if sh ./gradlew "package$1"; then
        echo
        echo "  Done. The zip is in $(pwd)/build/distributions"
    else
        echo
        echo "  The build failed; the reason is above."
    fi
    pause
}

pause() { echo; printf '  Press Enter to go back to the menu. '; read -r _; }

while true; do
    clear 2>/dev/null
    cat <<MENU

  LegacyCraft build console                       version $(lc_version)
  ---------------------------------------------------------------

  Packages (game + launcher, ready to copy to another PC)
    1  Windows, 64- and 32-bit together (Windows 7 or later)
    2  Windows 64-bit
    3  Windows 32-bit
    4  Windows ARM64
    5  Linux x64
    6  Linux ARM64
    7  macOS Intel
    8  macOS Apple silicon
    9  All of the above

    V  Change the version (now $(lc_version))
    O  Open the folder with the packages
    R  Run the game from source
    T  Run the tests
    Q  Quit

MENU
    printf '  Pick one: '
    read -r pick || exit 0
    case "$pick" in
        1) package Windows "Windows (64- and 32-bit)" ;;
        2) package WindowsX64 "Windows 64-bit" ;;
        3) package WindowsX86 "Windows 32-bit" ;;
        4) package WindowsArm64 "Windows ARM64" ;;
        5) package LinuxX64 "Linux x64" ;;
        6) package LinuxArm64 "Linux ARM64" ;;
        7) package MacosX64 "macOS Intel" ;;
        8) package MacosArm64 "macOS Apple silicon" ;;
        9) package All "every OS" ;;
        [Vv]) printf '  New version (Enter keeps %s): ' "$(lc_version)"; read -r new
              [ -n "$new" ] && sh ./gradlew -q setLcVersion "-PlcVersion=$new"; pause ;;
        [Oo]) mkdir -p build/distributions
              if command -v open >/dev/null 2>&1 && [ "$(uname -s)" = Darwin ]; then open build/distributions
              elif command -v xdg-open >/dev/null 2>&1; then xdg-open build/distributions >/dev/null 2>&1
              else echo "  $(pwd)/build/distributions"; pause; fi ;;
        [Rr]) sh ./gradlew run; pause ;;
        [Tt]) sh ./gradlew test; pause ;;
        [Qq]) exit 0 ;;
    esac
done
