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

APK je podepsané standardním debug klíčem, takže je vhodné pro testování, ne pro
distribuci do Google Play.

## Testy

```bash
gradle :app:testDebugUnitTest
```

DSP jádro (`app/src/test/java/com/example/DspUnitTest.kt`) pokrývá FFT, biquad filtry,
whisper kompresor, STFT separaci hlasu, odečtení známé písničky, cepstrální
de-reverberaci a adaptivní LMS filtr.

## Poznámky

- Release build vyžaduje `KEYSTORE_PATH`, `STORE_PASSWORD` a `KEY_PASSWORD` v prostředí
  a vlastní `my-upload-key.jks`. Keystore se do repozitáře necommituje.
- Soubor `.env.example` obsahuje pouze komentovaný placeholder `GEMINI_API_KEY`.
  Aplikace žádné Gemini API nevolá ani nemá oprávnění `INTERNET`.
- Dokumentace: [`TECHNICAL_SPECIFICATION.md`](TECHNICAL_SPECIFICATION.md),
  [`DEV_LOG.md`](DEV_LOG.md).
