# AudioDetective Pro

Forenzní audio editor a spektrální analyzátor. Umožňuje čistit nahrávky, izolovat šepot a lidský
hlas, potlačit ozvěnu místnosti a dynamický hluk, odečíst známou písničku a vygenerovat forenzní
protokol. Vše běží 100% offline na zařízení, bez jakékoli sítě.

Projekt dnes obsahuje **dvě aplikace nad jedním DSP jádrem**:

| Aplikace | Modul | Cíl |
| --- | --- | --- |
| AudioDetective Pro (mobil) | `app` | Android telefon / tablet |
| AudioDetective Pro (desktop) | `desktop` | Windows PC (`.exe`) |

Obě používají stejný `com.example.dsp` balíček, stejné datové modely a stejný vizuální styl,
takže se změna DSP projeví na obou platformách a výsledek filtru je identický.

> Poznámka: mobilní část je **nativní Android aplikace** (Kotlin + Jetpack Compose). Nejde ji
> spustit v Expo ani v jiném React Native / JavaScript runtime.

## Windows aplikace (desktop)

Desktopový modul je Compose Desktop aplikace, která sdílí DSP jádro, modely a téma s Androidem.
Uspořádání odpovídá mobilní aplikaci: horní lišta, záložky **Home / Studio / Library / Effects /
Report** a spodní navigace.

Odlišnosti dané platformou:

| Oblast | Android | Windows |
| --- | --- | --- |
| Knihovna | interní sandbox + export do `Music/AudioDetective` | přímo `%USERPROFILE%\Music\AudioDetective` (knihovna = složka, hned viditelná v Průzkumníku) |
| Reporty | sdílení přes Android Share | textové protokoly do `%USERPROFILE%\Documents\AudioDetective\reports` |
| Formáty | WAV, MP3, M4A, AAC, OGG, FLAC (MediaCodec) | WAV, AU, AIFF, MP3, FLAC, OGG |
| Rychlost přehrávání | pitch lock | tape style (rychlost mění i výšku tónu) |

### Podporované formáty na Windows

`WAV` (8/16/24/32-bit PCM), `AU`, `AIFF`, `MP3`, `FLAC`, `OGG`. Formáty `.m4a`, `.aac`, `.wma`,
`.opus` a `.amr` aplikace neumí a při importu to řekne.

MP3, FLAC a OGG dekódují vlastní dekodéry (JLayer, jFLAC, VorbisSPI) volané přímo. Důvod: jejich
SPI providery se registrují přes `META-INF/services`, což `AudioSystem` na moderních JDK přestal
pro providery na classpath načítat, takže by import MP3 skončil chybou „nepodařilo se dekódovat“.

### Build a spuštění Windows aplikace

```bash
# rychlé spuštění ze zdrojáků
gradle :desktop:run

# testy dekodérů a paměťového rozpočtu
gradle :desktop:test

# samostatná aplikace (složka s .exe, nepotřebuje Javu v systému)
gradle :desktop:createDistributable
```

Výsledek je v `desktop/build/compose/binaries/main/app/AudioDetectivePro/AudioDetectivePro.exe`;
distribuuje se celá složka, protože obsahuje přiložený runtime. `packageExe` / `packageMsi` umí
navíc instalátor, ale MSI vyžaduje nainstalovaný WiX Toolset.

### Volitelné testovací vzorky

Testy formátů se spouští nad skutečnými soubory, když jim předáš cestu:

```bash
gradle :desktop:test -Dfixture.mp3=C:\cesta\vzorek.mp3 -Dfixture.flac=C:\cesta\vzorek.flac -Dfixture.ogg=C:\cesta\vzorek.ogg
```

Bez vzorků se přeskočí jen kontrola komprimovaných formátů; WAV testy běží vždy.

## Android aplikace

### Požadavky

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

Windows aplikaci staví workflow
[`.github/workflows/desktop-build.yml`](.github/workflows/desktop-build.yml) (artifact
**AudioDetectivePro-Windows**). Běží na Windows runneru, protože `jpackage` umí vytvořit `.exe`
jen tam.

APK je podepsané debug klíčem, takže je vhodné pro testování, ne pro
distribuci do Google Play.

## Kam se ukládají exporty

Každá nově vytvořená (vyčištěná) stopa se kromě interní knihovny aplikace publikuje do
veřejné složky **`Music/AudioDetective`**, takže je vidět ve správci souborů i v hudebním
přehrávači. Potvrzení v aplikaci vypíše přesnou cestu.

Soubor tedy existuje na dvou místech, což je záměr – interní kopie drží nedestruktivní
workflow a přehrávání, veřejná je pro uživatele:

| Kopie | Cesta na zařízení | K čemu |
| --- | --- | --- |
| Interní knihovna | `/data/data/com.aistudio.audiodetective.fznr/files/recordings/` | záznam v knihovně, přehrávání, navazující úpravy |
| Veřejný export | `/storage/emulated/0/Music/AudioDetective/` (též `/sdcard/Music/AudioDetective/`) | soubor pro uživatele – správce souborů, hudební přehrávač, přenos z telefonu |

V Android Studiu obě najdeš v **Device Exploreru** (`View → Tool Windows → Device Explorer`).
Interní cesta je přístupná jen u debug buildu, protože Device Explorer využívá `run-as`;
u release buildu ji systém nezpřístupní, a proto export do veřejné složky existuje.

- **Android 10+**: zápis přes MediaStore, bez oprávnění.
- **Android 9 a nižší**: přímý zápis + indexace mediálním skenerem, vyžaduje
  `WRITE_EXTERNAL_STORAGE` (deklarováno s `maxSdkVersion="28"`, aplikace si ho vyžádá při startu).

Původní nahrávka se nikdy nepřepisuje a export nikdy nepřepíše dřívější export stejného jména
(nový soubor dostane příponu `_1`, `_2`, …).

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

Testy pokrývají DSP jádro (`DspUnitTest.kt`) – FFT, biquad filtry, whisper kompresor, STFT
separaci hlasu, odečtení známé písničky, cepstrální de-reverberaci a adaptivní LMS filtr –
streamované načítání WAV (`WavHelperTest.kt`) – hlavička, downmix kanálů, přeskakování
chunků a odmítnutí příliš dlouhé nahrávky – export do zařízení (`AudioExporterTest.kt`)
a desktopové dekodéry včetně MP3/FLAC/OGG (`DesktopAudioDecoderTest.kt`):

```bash
gradle :app:testDebugUnitTest     # Android
gradle :desktop:test              # Windows desktop
```

## Poznámky

- Release build vyžaduje `KEYSTORE_PATH`, `STORE_PASSWORD` a `KEY_PASSWORD` v prostředí
  a vlastní `my-upload-key.jks`. Keystore se do repozitáře necommituje.
- Aplikace je plně offline: neobsahuje žádné síťové SDK (Firebase / Gemini / Retrofit)
  ani oprávnění `INTERNET`. Pozůstatky AI Studio scaffoldingu (KSP, Secrets a
  Google Services pluginy a nepoužité knihovny Room, Moshi, Firebase) byly odstraněny.
- Dokumentace: [`TECHNICAL_SPECIFICATION.md`](TECHNICAL_SPECIFICATION.md),
  [`DEV_LOG.md`](DEV_LOG.md).
