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
   - *Poznámka:* Ve Fázi 10 byl generátor i veškeré vestavěné demo nahrávky z aplikace odstraněny.

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
   - **Varianta B (Soubor známé písničky – z knihovny nebo telefonu)**: Analytik vybere referenční soubor známé skladby (`.mp3`, `.wav`, `.m4a`) z telefonu nebo z knihovny aplikace.
   - **Automatická časová synchronizace (Envelope Cross-Correlation)**: Algoritmus křížovou korelací obálek energie automaticky najde časový posun (`detectedOffsetMs`), kde písnička v nahrávce hraje, a vykreslí náhled spektrogramu naučeného vzorku.
2. **Krok 2 – Odečtení vzorku / písničky z celé nahrávky (`applyProfileSubtraction`)**:
   - Kombinuje dynamické časově synchronizované STFT rámce referenční skladby s naučeným spektrálním otiskem a adaptivním měřítkem hlasitosti místnosti (*Gain Match*).
   - **Ochrana řečových formantů (300–3400 Hz)**: Chrání tranzienty lidského hlasu, aby po odečtení hudby zůstala řeč čitelná a srozumitelná, a uloží vyčištěnou stopu (`_SongRemoved.wav` / `_ProfileCleaned.wav`).

### 🔹 Fáze 8: Extrémní forenzní filtrace – De-reverberace a Adaptivní LMS filtr (Sprint 8)
Implementovány dva pokročilé algoritmy pro extrémní situace, kde běžné biquad filtry ani stacionární spektrální brány nestačí:

1. **Inverzní filtr místnosti / De-reverberace (`CepstralDeverbProcessor.kt` & `CepstralDeverbCard.kt`)**:
   - **Forenzní detektivní princip**: Odposlechy z prázdných místností, kachličkových koupelen, toalet či chodeb trpí silnou ozvěnou (multipath reverberací), která slévá sykavky a ničí srozumitelnost mluveného slova.
   - **Matematický princip**: Cepstrální analýza v quefrenční oblasti ($C[n] = \text{IFFT}(\ln(|\text{FFT}(x)|))$). Algoritmus detekuje periodické špičky odrazů stěn v okně 10–120 ms, určí časový lag a sílu odrazu.
   - **Inverzní hřebenový filtr (Inverse Comb Filter)**: Aplikuje feedforward inverzní filtraci se sekundárním potlačením harmonických odrazů a obnovou třpytu řečových formantů pro „vysušení“ ozvěny.
   - **Testovací vzorek**: Otestováno na syntetickém scénáři s ozvěnou dlaždicové koupelny (generováno v testech, viz Fáze 10).

2. **Adaptivní LMS filtr pro dynamický šum (`AdaptiveLmsFilter.kt` & `AdaptiveLmsCard.kt`)**:
   - **Forenzní detektivní princip**: Spektrální brána (Fáze 2) čistí pouze stacionární šum (konstantní ventilátor). Při proměnlivém hluku aut na ulici, štěkání psa nebo větru spektrální brána selhává.
   - **Matematický princip**: Architektura ALE (Adaptive Line Enhancer) s Leaky NLMS (Normalized Least Mean Squares). Filtr s 48 FIR váhami v reálném čase adaptivně sleduje korelaci dynamického hluku a odčítá jej jako chybový signál $e[n] = d[n] - y[n]$.
   - **Ochrana řečových formantů**: Využívá okamžitou energii signálu ke zmrazení adaptace během znění samohlásek, takže lidská řeč zůstává plně chráněna a nedochází k jejímu poškození.
   - **Testovací vzorek**: Otestováno na syntetickém scénáři dynamického provozu na ulici (generováno v testech, viz Fáze 10).

---

### 🔹 Fáze 9: Živý DSP náhled při přehrávání (AudioTrack engine)
Dosavadní přehrávání běželo přes `MediaPlayer`, který neumí aplikovat žádné DSP. Efekty se projevily až při exportu vyčištěné kopie, takže analytik neslyšel výsledek filtru v reálném čase. Fáze 9 tento rozpor odstraňuje:

1. **Sdílený DSP řetězec (`AudioDspChain.kt`)**: Veškerá logika racku (STFT separace hlasu, HPF/LPF, spektrální brána, adaptivní LMS, cepstrální de-reverberace, whisper boost) je přesunuta do jedné stateless funkce. Používá ji jak export (`AudioDetectiveRepository.processAndSaveFilteredWav`), tak živý náhled. Tím je zaručeno, že **to, co analytik slyší, je přesně to, co se uloží na disk** (zásadní pro forenzní reprodukovatelnost).
2. **`ForensicAudioPlayer` přepsán na `AudioTrack`**: Audio se dekóduje do PCM, prožene sdíleným řetězcem a streamuje přes `AudioTrack` ve float chuncích s nízkou latencí.
3. **Živé přepočítání efektů**: Každá změna přepínače/posuvníku vyvolá debounced přepočet (150 ms) na pozadí. Pozice přehrávání se zachová a streamovací writer se plynule restartuje na stejném místě, takže změna je slyšet okamžitě.
4. **Zachovaná funkčnost transportu**: Rychlost 0.5x–1.5x s uzamčeným pitch přes `AudioTrack.setPlaybackParams`, přesné hledání, vzorkově přesná A-B smyčka a korektní detekce konce nahrávky.
5. **Chybová zpětná vazba**: Chyby dekódování/inicializace audio výstupu se už nepolykají přes `printStackTrace`, ale jsou vystaveny přes `errorMessage` a zobrazují se jako snackbar.
6. **Indikátor zpracování**: Studio zobrazuje nenápadný indikátor „Zpracovávám DSP náhled…“, dokud přepočet neběží.

---

### 🔹 Fáze 10: Odstranění demo nahrávek a AI Studio scaffoldingu
Aplikace po prvním spuštění automaticky generovala 8 syntetických demo nahrávek. Pro reálné
forenzní nasazení je to nežádoucí, proto byly kompletně odstraněny:

1. **Zrušen `SyntheticAudioGenerator.kt`** i jeho registr `SAMPLE_SPECS` a metoda
   `ensureInitialSamples()` v repository. Knihovna je nyní po instalaci **záměrně prázdná**
   a plní se výhradně uživatelským importem nebo nahrávkou (REC).
2. **Odstraněno pole `isSample`** z modelu `AudioFileItem`, protože rozlišovalo pouze demo vzorky.
3. **Přidány prázdné stavy**: Dashboard i Library nyní při prázdné knihovně zobrazí výzvu
   k importu vlastního audia místo prázdné obrazovky.
4. **Odstraněn AI Studio scaffolding**: nepoužívané závislosti (Firebase, Room, Moshi,
   Retrofit, OkHttp) a pluginy KSP, Secrets a Google Services. KSP v headless CI vyhazoval
   neškodnou, ale matoucí AWT výjimku. APK se tím zmenšilo z ~22,5 MB na ~16,8 MB.

---

### 🔹 Fáze 11: Streamované načítání audia a paměťový rozpočet (oprava pádu při importu)
Import nahrávky o velikosti ~230 MB shazoval aplikaci přes
`java.lang.OutOfMemoryError: Failed to allocate a 229674472 byte allocation` s pádem uvnitř
`AudioDecoder.decodeToPcm`. Příčinou bylo načtení celého souboru do paměti (`InputStream.readBytes()`)
a následná druhá kopie pole, ke které se teprve stavěl výsledný `FloatArray`.

**Provedená oprava:**

1. **`WavHelper` kompletně přepsán na streamování.** Nová metoda `readWavHeader()` přečte pouze RIFF
   hlavičku (přeskakuje neznámé chunky) a vrací `WavHeader` s počtem rámců. `readWav()` /
   `readWavFromStream()` čtou datový chunk po 64kB blocích a rovnou správně **downmixují** kanály
   do mono (dříve se bral pouze kanál 0). Metoda `readWavBytes()` byla odstraněna, aby se zakázaný
   vzor nemohl vrátit.
2. **`AudioMemoryBudget`** – nový centrální rozpočet: maximální velikost PCM je 30 % heapu procesu
   (zbytek musí pokrýt pracovní kopie DSP řetězce). Při překročení se hází `AudioTooLargeException`
   ještě **před** velkou alokací, takže uživatel dostane hlášku s konkrétním limitem v minutách.
3. **`AndroidManifest.xml`**: zapnuto `android:largeHeap="true"` (strop se zvedá z 192 MB na 576 MB).
4. **`AudioDecoder`**: WAV se z `content://` streamu sniffuje i dekóduje na témže streamu bez kopie;
   `MediaCodec` cesta už nehromadí vzorky do `ArrayList<Float>` (boxing), ale do rostoucího
   `FloatArray`, a délku kontroluje předem přes `KEY_DURATION`.
5. **Knihovna souborů**: `getAudioFiles()` už nečte celé soubory, ale jen hlavičky (`readWavHeader`),
   takže ani výpis knihovny s obřím WAV neshodí aplikaci. Pro ne-WAV soubory se délka bere z
   `MediaMetadataRetriever`. Metrika `Channels` nyní hlásí skutečný počet kanálů z hlavičky.
6. **OOM guardy**: těžké DSP kroky v repository (`runHeavyStep`) a živý náhled v přehrávači chytají
   `OutOfMemoryError` a hlásí chybu, místo aby proces umřel.
7. **Testy**: nový `WavHelperTest` (7 testů) ověřuje čtení hlavičky, downmix, přeskakování chunků
   a vyhození `AudioTooLargeException`.

---

### 🔹 Fáze 12: Export do veřejné složky `Music/AudioDetective`
Vyčištěné stopy se dosud ukládaly **jen do interního sandboxu aplikace**
(`filesDir/recordings`), takže je uživatel v žádném správci souborů ani hudebním přehrávači
nenašel – „export“ fakticky nikam nevedl.

**Provedená změna (`data/AudioExporter.kt`):**

1. **Každá nově vytvořená pracovní stopa se publikuje** do veřejné složky
   `Music/AudioDetective` – týká se to exportu z Dashboardu, čištění ve Studiu i v Effects
   (separace hlasu, de-reverb, LMS, odečtení referenčního vzorku).
2. **Android 10+ zapisuje přes MediaStore** (`RELATIVE_PATH = Music/AudioDetective`,
   `IS_PENDING` během zápisu), takže není potřeba žádné oprávnění. **Android 9 a nižší** zapisuje
   soubor přímo a nechá ho přes `MediaScannerConnection` zaindexovat; vyžaduje
   `WRITE_EXTERNAL_STORAGE` (deklarováno s `maxSdkVersion="28"`, vyžádáno při startu).
3. **Kopírování po 64kB blocích** ze souboru do souboru – export obří nahrávky tedy nikdy
   nenačítá celý obsah do paměti (navazuje na Fázi 11).
4. **Kolize názvů se neřeší přepsáním**: na Androidu 9- se přidává `_1`, `_2`, … (viz
   `AudioExporter.uniqueTarget`), na Androidu 10+ deduplikuje MediaStore.
5. **Potvrzení obsahuje cílovou cestu**: snackbar hlásí např.
   `Saved cleaned copy: ….wav → Music/AudioDetective/….wav`.
6. **Testy**: `AudioExporterTest` ověřuje pojmenování cílů a název cílové složky.

Interní kopie v knihovně aplikace zůstává zachována, aby fungovalo nedestruktivní workflow
a přehrávání; export je navíc plnohodnotná kopie pro uživatele (MD5 obou souborů je shodné).

---

### 🔹 Fáze 13: Windows desktop aplikace nad stejným DSP jádrem
Cílem bylo vytvořit z mobilní aplikace **aplikaci pro Windows**, ne ji přepsat. Ukázalo se, že
to jde levně, protože `com.example.dsp` je čistý Kotlin bez Android SDK (výjimkou byl
`AudioDecoder` závislý na `MediaCodec`, ten se přesunul do balíčku `com.example.audio`).

**Architektura (`desktop/` modul, Compose Desktop):**

1. **Sdílený kód místo kopie**: `build.gradle.kts` modulu přidává do svého source setu
   `app/src/main/java/com/example/dsp`, `.../model` a `.../ui/theme`. Fyzicky tedy existuje
   jediný soubor DSP jádra, jediné modely a jediná barevná paleta pro obě aplikace.
2. **Platformní vrstva jen tam, kde být musí**: `DesktopAudioDecoder` (javax.sound),
   `DesktopAudioPlayer` (`SourceDataLine` místo `AudioTrack`), `DesktopAudioRecorder`
   (`TargetDataLine`), `DesktopAudioRepository` (knihovna = `Music\AudioDetective`, reporty do
   `Documents\AudioDetective\reports`) a `DesktopViewModel`.
3. **Stejné UI uspořádání jako Android**: horní lišta, hero banner „Voice Cleaning Dashboard“,
   karty nahrávek s quick actions (Trim / Noise Reduction / Voice Boost / Export), záložky
   **Home / Studio / Library / Effects / Report** a spodní navigace – vše postavené na stejných
   `Forensic*` barvách, takže aplikace vypadá jako ta mobilní.
4. **Vlastní dekodéry pro komprimované formáty**: `Mp3Decoder` (JLayer), `FlacDecoder` (jFLAC),
   `OggDecoder` (VorbisSPI) volané přímo. Důvod je konkrétní chyba: tyto knihovny se registrují
   přes `META-INF/services`, ale `AudioSystem` od JDK 9 providery na classpath nenačítá, takže
   každý import MP3 skončil jako „nepodařilo se dekódovat“. WAV jde dál přes sdílený `WavHelper`
   (24/32-bit, downmix, paměťový rozpočet).
5. **Metriky knihovny**: `toItem()` dopočítá Peak, RMS, Noise floor a podíl energie v pásmu řeči
   ze skutečných vzorků (s cache podle `lastModified`), takže inspektor v Knihovně ukazuje stejná
   čísla jako na Androidu. Délka se bere z **počtu dekódovaných vzorků**, protože `frameLength`
   od MP3SPI je nespolehlivý (u minutového souboru hlásil 0,05 s).
6. **Distribuce**: `gradle :desktop:createDistributable` vytvoří složku s `AudioDetectivePro.exe`
   a přiloženým runtime (necelých 160 MB), takže aplikace běží i bez nainstalované Javy. CI
   workflow `desktop-build.yml` ji staví na Windows runneru (jpackage umí `.exe` jen tam).
7. **Testy**: `DesktopAudioDecoderTest` ověřuje WAV round trip, odmítnutí nepodporovaných
   přípon, paměťový rozpočet a – se vzorky přes `-Dfixture.mp3/flac/ogg` – že každý komprimovaný
   formát skutečně dekóduje zvuk (ne jen ticho).

---

## 🧪 3. Testování a verifikace

| Testovací sada | Výsledek | Popis |
| :--- | :---: | :--- |
| `DspUnitTest.testFftProcessor_detectsSinePeak` | ✅ PASSED | Ověření přesnosti FFT detekce 1000 Hz tónu s tolerancí < 60 Hz. |
| `DspUnitTest.testHighPassFilter_attenuatesLowFrequencies` | ✅ PASSED | Ověření útlumu 60 Hz brumu při aplikaci 300 Hz High-Pass filtru. |
| `DspUnitTest.testWhisperCompressor_boostsLowLevelSignals` | ✅ PASSED | Ověření dynamického zesílení tichého signálu na úrovni -40 dB. |
| `DspUnitTest.testVoiceSeparator_isolatesVocalFormants` | ✅ PASSED | Ověření nárůstu poměru vokální energie po STFT separaci hlasu od 50 Hz brumu. |
| `DspUnitTest.testReferenceAudioCanceller_subtractsKnownSongAndPreservesSpeech` | ✅ PASSED | Ověření 2-krokového naučení známé písničky a jejího odečtení při zachování řečového formantu. |
| `DspUnitTest.testCepstralDeverbProcessor_detectsAndDriesEcho` | ✅ PASSED | Ověření detekce zpoždění odrazu v koupelně (25 ms) a jeho útlumu inverzním hřebenovým filtrem. |
| `DspUnitTest.testAdaptiveLmsFilter_attenuatesDynamicNoise` | ✅ PASSED | Ověření adaptivní NLMS konvergence a potlačení proměnlivého hluku projíždějících vozidel. |
| `ExampleRobolectricTest` | ✅ PASSED | Ověření čtení stringových zdrojů v kontextu aplikace. |
| `WavHelperTest` (7 testů) | ✅ PASSED | Streamované čtení hlavičky, downmix kanálů do mono, přeskočení neznámých chunků, odmítnutí příliš dlouhé nahrávky a 16-bit round trip. |
| `AudioExporterTest` (4 testy) | ✅ PASSED | Cílové názvy exportu (`name.wav`, `name_1.wav`, …) a název cílové složky `Music/AudioDetective`. |
| `DesktopAudioDecoderTest` (6 testů) | ✅ PASSED | Desktopové dekódování: WAV round trip, odmítnutí `.m4a`, paměťový rozpočet, chybná data a reálné MP3/FLAC/OGG vzorky (musí dát nenulový signál). |
| `gradle :app:testDebugUnitTest` | ✅ SUCCESS | 20/20 testů zelených (lokálně, Gradle 9.3.1). |
| `gradle :desktop:test` | ✅ SUCCESS | 6/6 testů zelených, včetně dekódování MP3, FLAC a OGG. |
| `gradle :app:assembleDebug` | ✅ SUCCESS | Build APK bez chyb; ověřeno i spuštění na emulátoru (Android 37.1). |
| `gradle :desktop:createDistributable` | ✅ SUCCESS | Windows aplikace (`AudioDetectivePro.exe`) spuštěna samostatně, bez Javy v systému. |
| Export do `Music/AudioDetective` na zařízení | ✅ VERIFIED | Export z Dashboardu vytvořil `/sdcard/Music/AudioDetective/*.wav` s totožným MD5 jako interní kopie; MediaStore eviduje `relative_path=Music/AudioDetective/`. |

---

## 🚀 4. Plánované další kroky (Backlog)
- [ ] **Segmentové zpracování dlouhých nahrávek**: aplikace drží celou nahrávku v paměti
      (`FloatArray`), takže se do rozpočtu vejde přibližně 16 minut na 44,1 kHz. Pro delší záznamy
      (např. 20minutový soubor) je potřeba pracovat po úsecích (načítat a zpracovávat okno vzorků)
      místo celého souboru.
- [ ] Možnost exportu spektrogramu jako snímku ve vysokém rozlišení (PNG/PDF).
- [ ] Uživatelsky definované EQ křivky pro parametrický 5-pásmový ekvalizér.
- [ ] Export audio stop do formátu FLAC s bezeztrátovou kompresí.
