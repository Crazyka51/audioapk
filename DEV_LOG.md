# 📓 Vývojářský deník (Development Log) – AudioDetective Pro

**Projekt:** AudioDetective Pro (Android Forensic Audio Analysis)  
**Datum založení:** 7. října 2026  
**Autor / Správce:** AI Vývojářský Agent & Matěj Hrabák  
**Stack:** Android SDK (API 24–36), Kotlin 2.2+, Jetpack Compose, Material Design 3, Nativní DSP & FFT Engine, AudioRecord / AudioTrack, Robolectric & JUnit  

---

## 🎯 1. Kontext a cíl projektu

Cílem projektu je vytvořit specializovaný mobilní audio editor pro Android určený k **forenzní analýze zvukových záznamů**, detekci slabého šepotu a izolaci lidské řeči od konstantních ruchů (ventilace, motory, síťový brum 50/60 Hz).

### Klíčové pilíře implementace:
- **100% offline mobilní autonomie:** Všechny operace, FFT výpočty a DSP filtry běží lokálně v sandboxu zařízení bez odesílání dat do cloudu.
- **Nedestruktivní forenzní zpracování:** Původní nahrávky zůstávají netknuté, úpravy generují pracovní forenzní kopie s detailním certifikátem o provedených krocích.
- **Vizuální diagnostika v reálném čase:** Duální zobrazení – časová osa amplitudy (RMS osciloskop) a spodní spektrální tepelná mapa (FFT spektrogram) s frekvenční sondou pro okamžitý odečet v Hz a dBFS.

---

## 📅 2. Chronologický přehled fází a sprintů

### 🔹 Fáze 1: Inicializace a vizuální identita (Sprint 1)
- **Téma a vizuální styl:** Implementován přísný Dark Mode (`#0D0E12`, `#14161D`, `#1C1F28`) s vysokým kontrastem, neonově tyrkysovými (`#00E5FF`) a zelenými (`#00FF9D`) akcenty pro spektrální stopy a varovnou oranžovou (`#FF5722`) pro markery.
- **Adaptivní ikona:** Vygenerována unikátní forenzní adaptivní ikona se symbolem zvukové vlny a lupy v rozlišeních mdpi až xxxhdpi.
- **Konfigurace manifestu a oprávnění:** Nastavena oprávnění `RECORD_AUDIO`, `VIBRATE` a `WAKE_LOCK` bez zbytečných invazivních storage oprávnění (využit moderní Android Storage Access Framework).

### 🔹 Fáze 2: DSP & Audio Processing Core (Sprint 2)
Implementována vlastní knihovna pro digitální zpracování signálu (DSP) bez nutnosti externích těžkých nativních C++ wrapperů:
1. **`WavHelper.kt`**: Čtení a zápis 16-bit / 44.1 kHz PCM WAV se standardní hlavičkou RIFF/WAVE a normalizací do plovoucí řádové čárky `FloatArray [-1.0, 1.0]`.
2. **`AudioDecoder.kt`**: Univerzální dekodér s využitím `MediaExtractor` a `MediaCodec` pro přímý import formátů `.wav`, `.mp3`, `.m4a`, `.aac` a převod do mono PCM.
3. **`FftProcessor.kt`**: Radix-2 Cooley-Tukey FFT algoritmus (okno 1024 vzorků) s Hanningovým vyhlazením, bit-reverzním lookupem a logaritmickým přepočtem na dBFS.
4. **`BiquadFilter.kt`**: 2. řád IIR biquad filtrů (High-Pass 300 Hz, Low-Pass 4 000 Hz, Speech Bandpass, Notch) s Transposed Direct Form II pro numerickou stabilitu bez přechodových lupanců.
5. **`WhisperCompressor.kt`**: Forenzní dynamický expandér zesilující tiché pasáže a šepot (+3 dB až +30 dB) s obálkovým detektorem a měkkou limitací přes hyperbolický tangens.
6. **`SpectralNoiseGate.kt`**: Spektrální odčítání stacionárního šumového profilu pro vyčištění nahrávek od ventilace a motorů.
7. **`SyntheticAudioGenerator.kt`**: Generátor autentických forenzních testovacích případů (`Suspect_Whisper_Enhance.wav`, `Surveillance_Room4_Hum.wav`, `Interview_01.wav`).

### 🔹 Fáze 3: UI Komponenty a interaktivita (Sprint 3)
1. **`WaveformView.kt`**: Vektorový osciloskop vykreslující RMS obálku s neonovým prosvětlením, indikátorem přehrávací hlavy a interaktivním posunem (drag / tap).
2. **`SpectrogramCanvas.kt`**: 60 FPS Canvas spektrogram s logaritmickou frekvenční stupnicí (100 Hz, 500 Hz, 1 kHz, 2.5 kHz, 4 kHz, 8 kHz, 16 kHz) a dotykovou sondou (HUD s odečtem frekvence v Hz a decibelech).
3. **`AbLoopController.kt`**: Přesný kontrolér smyčky A-B s mikro-krokováním po ±10 ms pro detailní dešifrování problematických slov.
4. **`AudioCardItem.kt`**: Karty záznamů dle UI předlohy se stavovými štítky (*Original*, *Cleaned*, *HPF Active*, *Boosted*), přehrávačem a rychlými volbami.
5. **`LiveRecordingDialog.kt`**: Záznamové rozhraní s pulzujícím VU metrem v reálném čase.

### 🔹 Fáze 4: Obrazovky & Forenzní Certifikát (Sprint 4)
- **`DashboardScreen.kt`**: Přehledový dashboard s vyhledáváním, kartami nahrávek a rychlým REC tlačítkem.
- **`StudioScreen.kt`**: Duální vizualizér, transportní ovládání, přepínače rychlosti (0.5x–1.5x) a dok pro rychlé filtry.
- **`DspEffectsScreen.kt`**: Plný rack filtrů s posuvníky mezních frekvencí a intenzity.
- **`LibraryScreen.kt`**: Správce případů, import audia a detailní inspektor metadat (SNR, šumové dno, RMS, formát).
- **`ForensicReportScreen.kt`**: Generátor oficiálního forenzního protokolu s možností exportu a sdílení (Android Share Intent).

---

## 🧪 3. Testování a verifikace

| Testovací sada | Výsledek | Popis |
| :--- | :---: | :--- |
| `DspUnitTest.testFftProcessor_detectsSinePeak` | ✅ PASSED | Ověření přesnosti FFT detekce 1000 Hz tónu s tolerancí < 60 Hz. |
| `DspUnitTest.testHighPassFilter_attenuatesLowFrequencies` | ✅ PASSED | Ověření útlumu 60 Hz brumu při aplikaci 300 Hz High-Pass filtru. |
| `DspUnitTest.testWhisperCompressor_boostsLowLevelSignals` | ✅ PASSED | Ověření dynamického zesílení tichého signálu na úrovni -40 dB. |
| `ExampleRobolectricTest` | ✅ PASSED | Ověření čtení stringových zdrojů v kontextu aplikace. |
| `compile_applet` (Gradle assembleDebug) | ✅ SUCCESS | 100% čistý build bez varování a chyb. |

---

## 🚀 4. Plánované další kroky (Backlog)
- [ ] Možnost exportu spektrogramu jako snímku ve vysokém rozlišení (PNG/PDF).
- [ ] Uživatelsky definované EQ křivky pro parametrický 5-pásmový ekvalizér.
- [ ] Export audio stop do formátu FLAC s bezeztrátovou kompresí.
