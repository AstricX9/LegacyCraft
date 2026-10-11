Put the launcher packages here to host them on this site. They're the zips that
`gradlew package<Platform>` (or build.bat / build.sh) writes to build/distributions/:

  LegacyCraft-<version>-windows.zip        Windows 64- and 32-bit together
  LegacyCraft-<version>-windowsX64.zip     LegacyCraft-<version>-windowsX86.zip
  LegacyCraft-<version>-windowsArm64.zip
  LegacyCraft-<version>-macosArm64.zip     LegacyCraft-<version>-macosX64.zip
  LegacyCraft-<version>-linuxX64.zip       LegacyCraft-<version>-linuxArm64.zip

<version> is the "lc:" line of version.txt (set VERSION in js/downloads.js to match).
Any zip that isn't here is linked from the GitHub release tagged <version> instead,
so attaching the zips to that release works too.
