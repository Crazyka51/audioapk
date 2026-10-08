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

Každý běh CI si původně vygeneroval vlastní debug keystore, takže se podpis mezi buildy
lišil a Android odmítl instalaci přes předchozí verzi
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) – testování na zařízení pak znamenalo odinstalaci
a ztrátu dat aplikace.

Řešení: CI si před buildem nasadí **stejný** debug keystore, jaký používá tvůj počítač.

1. Vytvoř base64 svého lokálního klíče (PowerShell):

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore"))
   ```

2. V GitHubu otevři **Settings → Secrets and variables → Actions → New repository secret**.
3. Název `DEBUG_KEYSTORE_BASE64`, hodnota je výstup z kroku 1.
4. Spusť workflow znovu. V logu kroku *Report APK signing certificate* zkontroluj, že
   `certificate SHA-256 digest` odpovídá tvému klíči:

   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android
   ```

Dokud secret neexistuje, krok se přeskočí a build funguje dál (jen s náhodným klíčem).
Keystore se do repozitáře necommituje – žije pouze v GitHub Secrets.

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
