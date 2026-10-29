# Third-party notices

- Android SDK and Android Gradle Plugin: distributed under their respective Google terms and licenses.
- The X1 logo vector is part of this project source.

Native runtime files are generated/fetched by the GitHub Actions build and are not stored as binary files in this source tree.

- Native ARM64 XMRig executable: fetched at build time from [XMRig-for-Android source commit 9e227e960863057b230b9d6c5c517e4d4b934fba](https://github.com/XMRig-for-Android/xmrig-for-android/tree/9e227e960863057b230b9d6c5c517e4d4b934fba), Git blob SHA-1 `808c0a69c27b70620f47adc76a573e539715211d`. The executable is based on XMRig, licensed under GPL-3.0-or-later; see [XMRig source/license](https://github.com/xmrig/xmrig).
- Experimental native ARM64 Scrypt engine: built in CI from [JayDDee/cpuminer-opt commit b34565bfaca5da9ed894a326e3ba0dfe459550a4](https://github.com/JayDDee/cpuminer-opt/tree/b34565bfaca5da9ed894a326e3ba0dfe459550a4), licensed under GPL-2.0-or-later; see that repository's `COPYING` file. The upstream project documents AArch64/Linux support but does not officially support Android, so the NDK cross-build is experimental.
- The Scrypt build statically includes GMP 6.3.0 (GMP license, GPL-2.0-or-later option) and curl 8.16.0 (curl license). curl is built without TLS in this experimental target; Scrypt pool URLs must use plain `stratum+tcp://` until TLS is implemented and tested.
- When redistributing an APK containing these GPL components, retain the applicable license texts and provide the corresponding source/build scripts as required by those licenses.
