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
- **`DashboardScreen.kt`**: Přehledový dashboard s vyhledáváním, kartami nahrávek, přímým tlačítkem pro import souborů a rychlým REC tlačítkem.
- **`StudioScreen.kt`**: Duální vizualizér, transportní ovládání, přepínače rychlosti (0.5x–1.5x) a dok pro rychlé filtry.
- **`DspEffectsScreen.kt`**: Plný rack filtrů s posuvníky mezních frekvencí a intenzity.
- **`LibraryScreen.kt`**: Správce případů, import audia a detailní inspektor metadat (SNR, šumové dno, RMS, formát).
- **`ForensicReportScreen.kt`**: Generátor oficiálního forenzního protokolu s možností exportu a sdílení (Android Share Intent).

### 🔹 Fáze 5: Rozšíření importu audio souborů ze zařízení
- **Přímá podpora `content://` streamů pro WAV (8/16/24/32-bit PCM)**: V `WavHelper.readWavBytes()` a `AudioDecoder` doplněna přímá detekce RIFF/WAVE hlavičky ze vstupního streamu `ContentResolver`, aby šly bezeztrátové WAV soubory načítat okamžitě z úložiště telefonu bez závislosti na hardwarovém `MediaCodec`.
- **Dekódování komprimovaných formátů (`.mp3`, `.m4a`, `.aac`, `.ogg`, `.flac`)**: Automatický downmix vícekanálového (stereo) zvuku do forenzního mono PCM a uložení pracovní kopie do lokálního sandboxu aplikace.
- **Zachování původního názvu souboru**: Přes `OpenableColumns.DISPLAY_NAME` se z `ContentResolver` přebírá reálný název nahrávky vybrané v telefonu.
- **Rychlý přístup z Dashboardu i Knihovny**: Tlačítko **Import File / Import Audio** je dostupné jak přímo na hlavní obrazovce (*Home*), tak na kartě *Library*.

### 🔹 Fáze 6: Pokročilá FFT spektrální tepelná mapa a STFT Separace hlasu
- **Rozšířená komponenta `SpectrogramCanvas.kt` & `FftProcessor.kt`**:
  - **4 barevné palety tepelné mapy (`HeatmapPalette`)**: *Cyber Neon*, *Thermal IR*, *Viridis*, *X-Ray Mono* s pravostrannou dBFS kalibrační stupnicí (`0 dB` až `-88 dB`).
  - **Vokální zoom (`Pásmo řeči 0–6 kHz` vs. `Plné spektrum 0–22 kHz`)**: Detailní zobrazení základního tónu hlasu F0 (150 Hz) a vokálních formantů F1 (650 Hz), F2 (1.65 kHz) a F3 (2.85 kHz).
  - **Interaktivní frekvenční sonda s klasifikací pásma**: Dotyk i tažení prstem po tepelné mapě v reálném čase klasifikuje frekvenční oblast (Sub-bas/Brum, F0 Základ hlasu, Formanty F1–F3, Sykavky).
  - **10-pásmový okamžitý FFT analyzátor (`InstantFftBarAnalyzer`)**: Sloupcový graf spektra synchronizovaný s aktuální pozicí přehrávání.
- **STFT Separace hlasu (`VoiceSeparator.kt` & `VoiceSeparationCard.kt`)**:
  - Implementováno krátkodobé Fourierovo transformování (STFT, 1024 vzorků, 75% překryv) s Wienerovým spektrálním maskováním a Gaussovým vážením vokálních formantů F1–F3.
  - **5 režimů separace**: *Čistá lidská řeč*, *Detekce a separace šepotu*, *Mužský hlas (F0: 85–180 Hz)*, *Ženský / vyšší hlas (F0: 165–265 Hz)* a *Pouze pozadí (Inverzní forenzní maska)*.
  - Měřič poměru energie řeči vs. šumu pozadí a dominantního formantu přímo na kartě ve *Studiu* i *Effects* racku.

### 🔹 Fáze 7: Dvoukrokové odečtení známé písničky a naučeného spektrálního vzorku (`ReferenceAudioCanceller.kt`)
Implementován forenzní systém pro situace, kdy na dlouhém záznamu hraje v pozadí **známá písnička** (jejíž soubor má analytik k dispozici) nebo opakující se rušivý zvuk:
1. **Krok 1 – Získání spektrálního otisku a STFT matice (`LearnedSpectralProfile`)**:
   - **Varianta A (Vzorek z A–B úseku nahrávky)**: Analytik označí na časové ose úsek A–B (např. několik vteřin, kde zní jen hudba nebo šum bez řeči) a klepne na **„Vzorek z A–B“**. Algoritmus vypočítá průměrný i špičkový 512-pásmový FFT profil.
   - **Varianta B (Soubor známé písničky – z knihovny nebo telefonu)**: Analytik vybere referenční soubor známé skladby (`.mp3`, `.wav`, `.m4a` z telefonu nebo vzorový soubor `Known_Radio_Song_Reference.wav` k odposlechu `Cafe_Wiretap_With_Song.wav`).
   - **Automatická časová synchronizace (Envelope Cross-Correlation)**: Algoritmus křížovou korelací obálek energie automaticky najde časový posun (`detectedOffsetMs`), kde písnička v nahrávce hraje, a vykreslí náhled spektrogramu naučeného vzorku.
2. **Krok 2 – Odečtení vzorku / písničky z celé nahrávky (`applyProfileSubtraction`)**:
   - Kombinuje dynamické časově synchronizované STFT rámce referenční skladby s naučeným spektrálním otiskem a adaptivním měřítkem hlasitosti místnosti (*Gain Match*).
   - **Ochrana řečových formantů (300–3400 Hz)**: Chrání tranzienty lidského hlasu, aby po odečtení hudby zůstala řeč čitelná a srozumitelná, a uloží vyčištěnou stopu (`_SongRemoved.wav` / `_ProfileCleaned.wav`).

---

## 🧪 3. Testování a verifikace

| Testovací sada | Výsledek | Popis |
| :--- | :---: | :--- |
| `DspUnitTest.testFftProcessor_detectsSinePeak` | ✅ PASSED | Ověření přesnosti FFT detekce 1000 Hz tónu s tolerancí < 60 Hz. |
| `DspUnitTest.testHighPassFilter_attenuatesLowFrequencies` | ✅ PASSED | Ověření útlumu 60 Hz brumu při aplikaci 300 Hz High-Pass filtru. |
| `DspUnitTest.testWhisperCompressor_boostsLowLevelSignals` | ✅ PASSED | Ověření dynamického zesílení tichého signálu na úrovni -40 dB. |
| `DspUnitTest.testVoiceSeparator_isolatesVocalFormants` | ✅ PASSED | Ověření nárůstu poměru vokální energie po STFT separaci hlasu od 50 Hz brumu. |
| `DspUnitTest.testReferenceAudioCanceller_subtractsKnownSongAndPreservesSpeech` | ✅ PASSED | Ověření 2-krokového naučení známé písničky a jejího odečtení při zachování řečového formantu. |
| `ExampleRobolectricTest` | ✅ PASSED | Ověření čtení stringových zdrojů v kontextu aplikace. |
| `compile_applet` (Gradle assembleDebug) | ✅ SUCCESS | 100% čistý build bez varování a chyb. |

---

## 🚀 4. Plánované další kroky (Backlog)
- [ ] Možnost exportu spektrogramu jako snímku ve vysokém rozlišení (PNG/PDF).
- [ ] Uživatelsky definované EQ křivky pro parametrický 5-pásmový ekvalizér.
- [ ] Export audio stop do formátu FLAC s bezeztrátovou kompresí.
