# trackmygymgains.
App to track exercises, sets, reps, weights and rest periods at the gym.

## Requisiti

- **Android Studio** (versione recente, con AGP 9.x supportato)
- **JBR** (JetBrains Runtime) incluso in Android Studio: `C:\Program Files\Android\Android Studio\jbr`
- **Android SDK** installato tramite Android Studio, con platform 37 e platform-tools. Percorso predefinito: `C:\Users\<utente>\AppData\Local\Android\Sdk`
- Un AVD con id `Medium_Phone_API_37.0` (API 37) per i test strumentati (lista AVD: `emulator -list-avds`)

Il file `local.properties` (generato da Android Studio) indica il percorso dell'SDK e non va committato.

## Build

Da Android Studio: **Build > Make Project**, oppure da terminale (PowerShell):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

## Test da CLI

Lo script esegue i test JVM e, se necessario, avvia l'emulatore headless e lancia i test strumentati:

```powershell
.\scripts\run-tests.ps1               # test JVM + test strumentati su Medium_Phone_API_37.0
.\scripts\run-tests.ps1 -UnitOnly     # solo test JVM (nessun emulatore)
.\scripts\run-tests.ps1 -Avd Pixel_8  # usa un altro AVD
```

Lo script imposta `JAVA_HOME` sulla JBR di Android Studio (se non e' gia' impostato su un JDK valido), termina con codice diverso da zero se un test fallisce e stampa i percorsi dei report HTML.

Comandi Gradle diretti:

```powershell
.\gradlew.bat testDebugUnitTest          # test JVM (Robolectric)
.\gradlew.bat connectedDebugAndroidTest  # test strumentati (richiede un dispositivo/emulatore connesso)
```

Report HTML:

- JVM: `app\build\reports\tests\testDebugUnitTest\index.html`
- Strumentati: `app\build\reports\androidTests\connected\debug\index.html`

## Test da Android Studio

- **Test JVM**: eseguili da `app/src/test` (clic destro sulla cartella > **Run Tests**). Non richiedono emulatore.
- **Test strumentati**: eseguili da `app/src/androidTest` (clic destro sulla cartella > **Run Tests**) con il dispositivo `Medium_Phone_API_37.0` selezionato nel menu dei dispositivi.

## Struttura cartelle

```
app/
  src/main/          codice dell'app, risorse, tema Compose
  src/test/          test JVM (JUnit, Robolectric)
  src/androidTest/   test strumentati su dispositivo/emulatore
  schemas/           schemi Room esportati
scripts/
  run-tests.ps1     esegue i test da CLI (con avvio emulatore)
gradle/
  libs.versions.toml catalogo delle versioni delle dipendenze
```
