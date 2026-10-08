# 📑 AudioDetective Pro – Technická specifikace a Produktová dokumentace

Tento dokument slouží jako komplexní technická a produktová „kuchařka“ pro další vývoj, rozšiřování, údržbu a architekturu mobilní aplikace **AudioDetective Pro**.

---

## 🧭 1. Produktové zadání & Produktový Brief

### 1.1 Vize a poslání produktu
**AudioDetective Pro** je specializovaný mobilní forenzní audio editor a spektrální analyzátor pro operační systém Android. Jeho primárním posláním je umožnit forenzním vyšetřovatelům, audio inženýrům, právníkům, novinářům i soukromým detektivům **dešifrovat nesrozumitelné zvukové nahrávky přímo v terénu na mobilním zařízení** bez nutnosti stolního DAW studia a bez odesílání citlivých důkazních materiálů do cloudu.

### 1.2 Klíčové uživatelské persony (Target Audience)
1. **Forenzní audio vyšetřovatel / Kriminalista**:
   - *Cíl:* Izolovat šepot nebo tichou konverzaci podezřelých z odposlechu skrytého mikrofonu.
   - *Potřeba:* Záruka nedestruktivního zpracování, auditní stopa, export forenzního certifikátu s hash kódy.
2. **Právní zástupce / Obhájce**:
   - *Cíl:* Ověřit autenticitu a srozumitelnost důkazní nahrávky z policejního zásahu či skrytého diktafonu.
   - *Potřeba:* Rychlé srovnání originálu vs. vyčištěné stopy (A/B testing), přehledný report.
3. **Novinář / Investigativní reportér**:
   - *Cíl:* Vyčistit terénní rozhovor pořízený na rušné ulici, v kavárně nebo v prázdné místnosti s ozvěnou.
   - *Potřeba:* Schopnost odečíst hrající rádiovou písničku nebo hluk motorů.

### 1.3 Hlavní produktové pilíře
* **100% Offline autonomie:** Žádná data nikdy neopouštějí zařízení. Všechny FFT výpočty a DSP filtry běží v nativním Kotlinu na procesoru zařízení.
* **Nedestruktivní workflow:** Původní důkazní nahrávka je chráněna jako Read-Only. Každá úprava vytváří novou forenzní pracovní stopu s jasným pojmenováním (`_Cleaned.wav`, `_SongRemoved.wav`, `_Deverbed.wav`).
* **Vizuální diagnostika v reálném čase:** Duální zobrazení – časová osa (RMS osciloskop s prosvětlením) a spodní spektrální tepelná mapa (60 FPS FFT spektrogram) s dotykovou sondou frekvencí a decibelů.
* **Forenzní certifikace:** Automatické generování úředního protokolu o akustickém zpracování s metrikami zlepšení SNR a přehledem aplikovaných filtrů.

---

## 🏗️ 2. Technická specifikace a systémová architektura

### 2.1 Technologický stack
* **Platforma:** Android (minSdk: 24, targetSdk: 36, compileSdk: 36)
* **Jazyk:** Kotlin 2.2+ (100% čistý Kotlin bez externích C++ NDK wrapperů pro maximální přenositelnost)
* **UI Framework:** Jetpack Compose s Material Design 3 (M3)
* **Architektura:** MVVM (Model-View-ViewModel) + Single Source of Truth přes Kotlin Coroutines a `StateFlow`
* **Audio I/O:**
  * Přehrávání: `AudioTrack` s nízkou latencí a streamingem přes bufferované float chunky.
  * Záznam: `AudioRecord` v 44.1 kHz / 16-bit mono PCM.
  * Dekódování souborů: `AudioDecoder` (využívá hardwarový `MediaExtractor` a `MediaCodec` s podporou `.wav`, `.mp3`, `.m4a`, `.aac`, `.ogg`, `.flac`) s fallbackem na přímý čteč WAV pro nulovou latenci.
* **Testování:** JUnit 4 + Robolectric pro lokální JVM testování celého DSP řetězce.

### 2.2 Adresářová struktura (`com.example`)
```
app/src/main/java/com/example/
├── MainActivity.kt               # Vstupní bod aplikace, správa Edge-to-Edge a NavHostu
├── audio/
│   ├── AudioRecorderManager.kt   # Záznam přes AudioRecord (44.1 kHz / 16-bit mono PCM)
│   └── ForensicAudioPlayer.kt    # AudioTrack streaming engine + živý DSP náhled celého
│                                 #   racku, přesný transport, A-B smyčka, pitch-locked speed
├── data/
│   └── AudioDetectiveRepository.kt # Práce se soubory v lokálním úložišti, metadata, správa stop
├── dsp/
│   ├── AdaptiveLmsFilter.kt      # Adaptivní Leaky NLMS filtr pro proměnlivý hluk (auta, vítr)
│   ├── AudioDecoder.kt           # Univerzální dekodér audio formátů do FloatArray PCM
│   ├── AudioDspChain.kt          # Sdílený DSP řetězec (export i živý náhled) – jediný zdroj pravdy
│   ├── BiquadFilter.kt           # IIR filtry 2. řádu (HPF, LPF, Speech Bandpass, Notch)
│   ├── CepstralDeverbProcessor.kt# Cepstrální analýza odrazů a inverzní hřebenový de-reverb filtr
│   ├── FftProcessor.kt           # Radix-2 Cooley-Tukey FFT, Hanningovo okno, spektrální analýza
│   ├── ReferenceAudioCanceller.kt# 2-krokové odečtení známé písničky a křížová korelace
│   ├── SpectralNoiseGate.kt      # Spektrální subtrakce stacionárního šumu (ventilace, brum)
│   ├── VoiceSeparator.kt         # STFT separace řeči, izolace formantů a hlasové maskování
│   ├── WavHelper.kt              # Standardní RIFF/WAVE IO operace (16-bit PCM normalizace)
│   └── WhisperCompressor.kt      # Zesilovač šepotu s měkkou limitací přes tanh()
├── model/
│   └── AudioModels.kt            # Datové modely (AudioFileItem, DspConfig, LoopRegion, atd.)
├── ui/
│   ├── components/
│   │   ├── AbLoopController.kt   # UI panel pro jemné ladění A-B smyčky (±10 ms kroky)
│   │   ├── AdaptiveLmsCard.kt    # Karta pro ovládání dynamického LMS filtru
│   │   ├── AudioCardItem.kt      # Karty stop v knihovně a dashboardu
│   │   ├── CepstralDeverbCard.kt # Karta pro potlačení ozvěny v místnosti
│   │   ├── LiveRecordingDialog.kt# Dialog pro záznam zvuku s živým VU metrem
│   │   ├── ReferenceSubtractionCard.kt # Karta pro odečtení známé hudební skladby
│   │   ├── SpectrogramCanvas.kt  # 60 FPS spektrální tepelná mapa s dotykovou sondou
│   │   ├── VoiceSeparationCard.kt# Karta pro STFT separaci lidského hlasu
│   │   └── WaveformView.kt       # Interaktivní osciloskop s RMS obálkou
│   ├── screens/
│   │   ├── DashboardScreen.kt    # Hlavní přehledová obrazovka (případy, rychlý import)
│   │   ├── DspEffectsScreen.kt   # Kompletní rack filtrů s plnými posuvníky
│   │   ├── ForensicReportScreen.kt # Generátor a exportér forenzního certifikátu
│   │   ├── LibraryScreen.kt      # Knihovna souborů a inspektor detailních metadat
│   │   └── StudioScreen.kt       # Forenzní studio – duální vizualizér a dok nástrojů
│   └── theme/
│       ├── Color.kt              # Barevná paleta (Dark Forensic Neon)
│       └── Theme.kt              # Material 3 motiv aplikace
└── viewmodel/
    └── AudioDetectiveViewModel.kt# Centrální ViewModel s veškerou orchestrací stavu a DSP
```

---

## 🎛️ 3. Katalog DSP algoritmů a jejich principy

| Algoritmus / Třída | Princip fungování | Kdy se v praxi použije |
|---|---|---|
| **`BiquadFilter.kt`** | IIR filtr 2. řádu s Transposed Direct Form II. Obsahuje High-Pass (300 Hz), Low-Pass (3400 Hz), Speech Bandpass (300–3400 Hz) a Notch filtry (50 Hz / 60 Hz). | Odstranění síťového brumu ze zásuvek, dunění větru na mikrofonu nebo vysokofrekvenčního syčení. |
| **`WhisperCompressor.kt`** | Nelineární dynamický expandér s obálkovým detektorem a kompresí špiček přes `tanh()`. Zesiluje signály pod prahem (-40 dB) až o +30 dB. | Zesílení nezřetelného šepotu podezřelého bez přebuzení hlasitých okolních zvuků (výstřely, bouchnutí dveří). |
| **`SpectralNoiseGate.kt`** | Spektrální odčítání v STFT oblasti. Odhadne šumové dno a aplikuje frekvenčně závislý útlum pod prahem. | Konstantní stacionární šum na pozadí (hučení klimatizace, větrák počítače, motor běžícího auta). |
| **`VoiceSeparator.kt`** | STFT s Wienerovým vážením a Gaussovským posílením formantů F1 (300–900 Hz), F2 (900–2500 Hz) a F3 (2500–3500 Hz). | Rozdělení nahrávky na lidskou řeč a pozadí. Izolace mužského či ženského hlasu. |
| **`ReferenceAudioCanceller.kt`** | 2-krokový systém: 1. Naučení spektrálního profilu skladby (nebo výběr referenčního MP3/WAV souboru) a křížová korelace obálek pro časové zarovnání. 2. Spektrální odečtení s ochranou formantů řeči. | Situace, kdy je v odposlechu z kavárny slyšet hlasitá písnička z rádia a analytik má soubor dané písničky k dispozici. |
| **`CepstralDeverbProcessor.kt`** | Cepstrální analýza v quefrenční oblasti ($C = \text{IFFT}(\ln\vert\text{FFT}\vert)$) pro detekci periodicity odrazů (10–120 ms) + inverzní hřebenový filtr. | Odposlechy z prázdných místností, kachličkových koupelen a chodeb, kde ozvěna slévá sykavky a ničí srozumitelnost. |
| **`AdaptiveLmsFilter.kt`** | Adaptivní Line Enhancer s algoritmem Leaky NLMS s 48 FIR váhami a dynamickým zmrazením adaptace při detekci samohlásek. | Proměnlivý nestacionární hluk na pozadí (projíždějící automobily, štěkání psa, proměnlivý hluk ulice). |

---

## 🍳 4. Vývojářská „Kuchařka“ (How-To Recipes)

Tato sekce obsahuje přesné postupy krok za krokem pro nejčastější úkony při dalším rozvoji aplikace.

### 4.1 Recept 1: Jak přidat nový DSP filtr do aplikace
Pokud chceš implementovat nový filtr (např. *Parametrický 5-pásmový EQ* nebo *De-Esser pro potlačení sykavek*):
1. **Vytvoř DSP procesor v balíčku `com.example.dsp`**:
   - Třída by měla mít metodu `process(samples: FloatArray, ...): FloatArray`.
   - Vstupní i výstupní pole jsou normalizované vzorky v rozsahu `[-1.0f, 1.0f]`.
2. **Rozšiř datový model `DspConfig` v `AudioModels.kt`**:
   - Přidej parametry filtru (např. `val deEsserEnabled: Boolean = false`, `val deEsserThreshold: Float = -24.0f`).
3. **Zapoj filtr do pipeline v `AudioDetectiveRepository.kt`**:
   - V metodě `processAndSaveFilteredWav(sourceFile, config)` přidej volání tvého procesoru do řetězce úprav.
   - Příklad:
     ```kotlin
     if (config.deEsserEnabled) {
         val deEsser = DeEsserProcessor(...)
         samples = deEsser.process(samples, config.deEsserThreshold)
     }
     ```
4. **Přidej obslužné metody do `AudioDetectiveViewModel.kt`**:
   - Metody `setDeEsserEnabled(Boolean)` a `setDeEsserThreshold(Float)`, které aktualizují `_dspConfig.value` a propagují změnu do `player.updateDspConfig()`.
5. **Vytvoř UI komponentu v `com.example.ui.components`**:
   - Vytvoř kartu `DeEsserCard.kt` s přepínačem (Switch), posuvníkem (Slider) a informačním štítkem.
6. **Umísti kartu do obrazovek**:
   - Vlož kartu do `StudioScreen.kt` a `DspEffectsScreen.kt`.
7. **Napiš jednotkový test do `DspUnitTest.kt`**:
   - Otestuj chování filtru na syntetickém tónu a ověř útlum cílového pásma.

---

### 4.2 Recept 2: Jak přidat vlastní testovací nahrávku do knihovny
Aplikace po startu **negeneruje žádné demo nahrávky** – knihovna je záměrně prázdná, dokud
analytik nedodá reálný důkazní materiál. Postup:
1. Připrav si zvukový soubor ve formátu, který dekodér podporuje (`.wav`, `.mp3`, `.m4a`, `.aac`, `.ogg`, `.flac`).
2. V aplikaci stiskni **Import Audio** (Dashboard nebo Library) a vyber soubor ze zařízení.
3. Aplikace soubor dekóduje do mono PCM 44,1 kHz, uloží pracovní kopii do interního
   sandboxu (`filesDir/recordings`) a zobrazí ji v knihovně jako `Original`.
4. Originál zůstává nedotčený; veškeré úpravy vytvářejí nové pracovní stopy.

> Pro reprodukovatelné testy DSP jádra slouží jednotkové testy v `DspUnitTest.kt`, které si
> signály syntetizují přímo v kódu (sine, formanty, ozvěna). Repozitář nedodává žádné
> předgenerované zvukové soubory.

---

### 4.3 Recept 3: Jak bezpečně pracovat s pamětí při zpracování velkých nahrávek
Vzhledem k tomu, že nahrávky mohou mít desítky minut:
* **Nikdy nenačítej celý soubor do paměti**: `File.readBytes()` / `InputStream.readBytes()` na WAV soubor je zakázaný vzor. Velká nahrávka (např. 230 MB) při tom shodí aplikaci přes `OutOfMemoryError`, protože se alokuje `ByteArray` o velikosti souboru a často ještě jeho druhá kopie. Pro metadata (délka, sample rate, kanály, bitová hloubka, počet rámců) používej výhradně `WavHelper.readWavHeader()` – přečte jen RIFF hlavičku a přeskočí datový chunk. Pro vzorky pak `WavHelper.readWav()` / `WavHelper.readWavFromStream()`, které data čtou po 64kB blocích a rovnou downmixují kanály do mono.
* **Paměťový rozpočet**: Maximální velikost PCM v paměti hlídej přes `AudioMemoryBudget` (výchozí 30 % heapu). Dekodér při překročení vyhodí `AudioTooLargeException` **před** velkou alokací, takže uživatel dostane srozumitelnou hlášku místo pádu. `android:largeHeap="true"` je v manifestu, takže strop je typicky 512–576 MB místo 192 MB.
* **Pozor na `ArrayList<Float>`**: každý vzorek zabalí do objektu (~16 B), pro dlouhou nahrávku tedy řádově více paměti než `FloatArray`. V dekodérech používej rostoucí `FloatArray`.
* **OOM guardy u těžkých DSP kroků**: Nejtěžší stupně (STFT separace hlasu, odečtení referenční stopy) alokují 1–2 další pole o plné délce. Tyto kroky obaluj do `runHeavyStep { }` (repository) nebo `catch (e: OutOfMemoryError)` (přehrávač), aby příliš dlouhá nahrávka zobrazila hlášku místo pádu procesu.
* **FloatArray vs DoubleArray**: V celém DSP jádru používej výhradně `Float` (32-bit), nikoli `Double` (64-bit). 10 minut 44.1 kHz mono zvuku zabere v RAM ve `FloatArray` cca **105 MB**.
* **Klonování polí**: Vyhni se zbytečnému volání `.clone()` nebo `.copyOf()`. Většinu operací (jako biquad filtry nebo zisk) lze provádět **in-place** přepisem vstupního pole:
  ```kotlin
  for (i in samples.indices) {
      samples[i] = filter.processSample(samples[i])
  }
  ```
* **Asynchronní výpočty**: Všechny těžké operace (FFT, generování spektrogramu, de-reverb) musí běžet na `Dispatchers.Default` přes `viewModelScope.launch`, nikdy ne na hlavním UI vlákně (`Dispatchers.Main`).

---

### 4.4 Recept 4: Jak ověřit a zkompilovat projekt
Projekt má nastavené rychlé lokální testy a striktní pravidla:
1. **Ověření kompilace**:
   - Použij nástroj `compile_applet`.
2. **Spuštění kompletní sady jednotkových testů**:
   - Spusť přes `run_command`:
     ```bash
     gradle :app:testDebugUnitTest
     ```
   - *Poznámka:* Vždy používej `gradle`, nikdy `./gradlew`.
3. **Kontrola manifestu a zdrojů**:
   - Pokud měníš název aplikace v `strings.xml`, **musíš** synchronně upravit také `metadata.json`.

---

## 🚀 5. Doporučený plán pro další verze (Roadmap & Backlog)

### 📌 Fáze 9: Export a grafické protokoly (Nejbližší sprint)
- [ ] **Export Spektrogramu do obrázku (PNG / PDF)**: Možnost uložit aktuální spektrální tepelnou mapu s frekvenční sondou jako grafickou přílohu k forenznímu protokolu.
- [ ] **Podpora bezeztrátového exportu FLAC**: Rozšíření exportu o formát FLAC pro archivaci bez kompresních artefaktů.

### 📌 Fáze 10: Pokročilé akustické nástroje
- [ ] **5-pásmový plně parametrický ekvalizér**: Interaktivní vizuální křivka v Compose s možností táhnout řídicí body (Gain, Q-faktor, Frequency).
- [ ] **De-Esser (Forenzní potlačovač sykavek)**: Cílené potlačení ostrých sykavek (5–9 kHz) při silném dynamickém zesílení šepotu.
- [ ] **Korekce fázového posunu (Stereo Phase Alignment)**: Pro vícekanálové nahrávky umožnit fázové zarovnání stop z různých mikrofonů.

---

## 🔒 6. Bezpečnostní a právní standardy forenzního audia
1. **Integrita důkazních materiálů**: Originální nahrávka importovaná uživatelem se **nikdy nepřepisuje**. Aplikace k ní přistupuje výhradně v režimu čtení.
2. **Auditní stopa v metadatech**: Každý vygenerovaný vyčištěný soubor obsahuje v popisu řetězec provedených transformací (např. `SRC: Case1.wav | FILTERS: HPF_300Hz, STFT_VOICE_ISOLATE_85%, DEVERB_25ms`).
3. **Lokální zpracování**: Aplikace nemá a nesmí mít žádná síťová oprávnění (`android.permission.INTERNET` není v manifestu povoleno), což garantuje 100% soulad s GDPR a ochranu utajovaných informací.
