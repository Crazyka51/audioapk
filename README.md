# AudioDetective Pro

Forenzní audio editor a spektrální analyzátor pro Android. Umožňuje čistit nahrávky,
izolovat šepot a lidský hlas, potlačit ozvěnu místnosti a dynamický hluk, odečíst
známou písničku a vygenerovat forenzní protokol. Vše běží 100% offline v zařízení.

> Poznámka: toto je **nativní Android aplikace** (Kotlin + Jetpack Compose). Nejde ji
> spustit v Expo ani v jiném React Native / JavaScript runtime.

## Požadavky

- Android Studio (doporučeno) nebo Gradle 9.3.1 + JDK 17+
- Android SDK s API 36 (`compileSdk`/`targetSdk` = 36, `minSdk` = 24)

## Build a spuštění lokálně

1. Otevři projekt v Android Studio (**Open**) a nech ho dosynchronizovat Gradle.
2. Spusť aplikaci na emulátoru nebo fyzickém zařízení (Run).

Pro build z příkazové řádky (vyžaduje lokálně nainstalovaný Gradle, wrapper v repu není):

```bash
gradle :app:assembleDebug
```

Výsledné APK najdeš v `app/build/outputs/apk/debug/app-debug.apk`.

## Získání APK přes GitHub Actions (bez lokálního Android SDK)

Workflow [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml)
sestaví debug APK na každý push do `main` a lze ho spustit i ručně:

1. Otevři záložku **Actions** v repozitáři.
2. Vyber workflow **Android CI** a klikni na **Run workflow**.
3. Po dokončení stáhni artifact **AudioDetective-debug-apk** (obsahuje `app-debug.apk`).

APK je podepsané debug klíčem, takže je vhodné pro testování, ne pro
distribuci do Google Play.

### Stabilní podpis APK (aby šly aktualizace přes sebe)

Android povolí instalaci nové verze přes předchozí jen tehdy, když je podepsaná **stejným
klíčem**. AGP si standardně generuje debug klíč zvlášť pro každý počítač i pro každý běh CI,
takže se podpisy lišily a instalace končila chybou
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` (a testování na zařízení znamenalo odinstalaci
a ztrátu dat aplikace).

Proto je v repozitáři **commitnutý společný debug keystore** `app/debug.keystore` a build ho
používá pro debug variantu na všech strojích:

| Parametr | Hodnota |
| --- | --- |
| Soubor | `app/debug.keystore` |
| Alias | `androiddebugkey` |
| Heslo (store i key) | `android` |
| Platnost | 30 let |

Otisk certifikátu (SHA-256):

```
2B:08:06:34:79:66:5F:3A:8A:E3:43:4D:75:54:27:34:E9:B8:22:71:87:42:C4:42:1C:2D:B6:23:99:6D:9F:10
```

Ověření, že APK z CI má stejný podpis jako lokální build:

```powershell
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\apksigner.bat"
& $apksigner verify --print-certs "$env:USERPROFILE\Downloads\audioapk-apk\app-debug.apk"
```

> **Pozor:** jde o debug klíč s veřejně známým heslem – nikdy jím nepodepisuj release build.
> Ten používá samostatný `my-upload-key.jks` (viz Poznámky níže).

## Testy

```bash
gradle :app:testDebugUnitTest
```

Testy pokrývají DSP jádro (`DspUnitTest.kt`) – FFT, biquad filtry, whisper kompresor, STFT
separaci hlasu, odečtení známé písničky, cepstrální de-reverberaci a adaptivní LMS filtr –
a streamované načítání WAV (`WavHelperTest.kt`) – hlavička, downmix kanálů, přeskakování
chunků a odmítnutí příliš dlouhé nahrávky.

## Poznámky

- Release build vyžaduje `KEYSTORE_PATH`, `STORE_PASSWORD` a `KEY_PASSWORD` v prostředí
  a vlastní `my-upload-key.jks`. Keystore se do repozitáře necommituje.
- Aplikace je plně offline: neobsahuje žádné síťové SDK (Firebase / Gemini / Retrofit)
  ani oprávnění `INTERNET`. Pozůstatky AI Studio scaffoldingu (KSP, Secrets a
  Google Services pluginy a nepoužité knihovny Room, Moshi, Firebase) byly odstraněny.
- Dokumentace: [`TECHNICAL_SPECIFICATION.md`](TECHNICAL_SPECIFICATION.md),
  [`DEV_LOG.md`](DEV_LOG.md).
