# QuickJSON

QuickJSON è un editor JSON Android nativo, privato e completamente offline. Offre una vista codice e un albero visuale sincronizzati, conserva anche le bozze momentaneamente non valide e usa il selettore documenti Android senza richiedere accesso generale ai file.

Non dichiara il permesso `INTERNET` e non integra analytics, pubblicità, account o servizi cloud.

## Funzioni

- documenti recenti, ricerca, rinomina inline, duplicazione ed eliminazione annullabile;
- creazione rapida di object, array, contenuto degli appunti o template;
- editor codice con numeri di riga, evidenziazione, linea/colonna dell’errore, format 2/4 spazi, minify, copy e undo/redo;
- editor visuale ricorsivo per tutti i tipi JSON, con modifica, cambio tipo, aggiunta, duplicazione, eliminazione e riordino;
- rilevamento delle chiavi duplicate prima della conversione ad albero;
- import con `ACTION_OPEN_DOCUMENT`, `ACTION_VIEW`, condivisione e appunti; export, share con `FileProvider` e salvataggio esplicito del file collegato;
- rilevamento dei conflitti esterni tramite hash SHA-256 e data, con Ricarica, Sovrascrivi e Salva con nome;
- limite import 5 MiB e decodifica UTF-8 rigorosa;
- colori dinamici Material 3, tema sistema/chiaro/scuro e UI inglese/italiana.

## Requisiti e build locale

- JDK 17
- Android SDK Platform 37.0 preview (`platforms;android-37.0`, canale canary)
- Android SDK Build Tools 36.0.0 o successivi

Il Wrapper blocca Gradle 9.5.0 e ne verifica il checksum. AGP 9.3.2 usa il supporto Kotlin integrato (Kotlin 2.2.10); le altre versioni sono nel version catalog.

```bash
sdkmanager --channel=3 "platforms;android-37.0" "build-tools;36.0.0"
./gradlew testDebugUnitTest lintDebug assembleDebug
```

L’APK debug si trova in `app/build/outputs/apk/debug/`.

Per i test strumentati, avviare un emulatore API 31 o 37 e usare:

```bash
./gradlew connectedDebugAndroidTest
```

## Architettura

Il progetto ha un solo modulo `app` e dependency injection manuale tramite `AppContainer`.

```text
Compose UI → MainViewModel / StateFlow → DocumentRepository → Room
                    │                         └──────────────→ DataStore
                    ├→ JsonEngine / JsonTree (dominio puro)
                    └→ FileGateway → ContentResolver / SAF / FileProvider
```

Il testo raw è sempre la fonte persistita. Il modello `JsonElement` viene derivato solo quando il testo è valido e viene serializzato nuovamente soltanto dopo una modifica visuale. L’autosave ha debounce di 500 ms ed è forzato quando l’Activity va in background; tab, selezione e ultimo documento vengono ripristinati dopo ricreazione o process death.

Gli schemi Room esportati sono in `app/schemas`; ogni cambio di schema deve includere una migrazione e il relativo test.

## Test

La suite copre parsing dei tipi JSON, Unicode ed escape, errori, chiavi duplicate, format/minify, operazioni e riordino dell’albero, undo/redo e conflitti esterni. I test Android coprono Room/Flow, bozze non valide, migrazione, flusso Compose e SAF tramite un `DocumentsProvider` finto (I/O, UTF-8, dimensione e permesso revocato).

La CI viene eseguita su pull request e push a `master`, con Wrapper validation, test, lint, APK debug e test strumentati su API 31 e 36. Compila con Android SDK 37.0 dal canale canary, richiesto dalle dipendenze Compose. Dopo il successo dell'intera matrice su `master`, crea, firma, verifica e pubblica come artifact l'APK `release`, con checksum SHA-256 e mapping R8. Le pull request non hanno accesso ai secret di firma. Tutte le Actions sono bloccate a SHA completo e gli artifact temporanei scadono dopo sette giorni.

## Release firmata

Creare una chiave una sola volta, conservarla cifrata fuori dalla repository e predisporre un backup sicuro:

```bash
keytool -genkeypair -v -keystore quickjson-release.jks -alias quickjson \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 quickjson-release.jks
```

Configurare nei GitHub Actions Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Non cambiare il keystore nelle versioni successive: una chiave diversa impedisce l’aggiornamento dell’app installata.

La pubblicazione accetta solo tag `vMAJOR.MINOR.PATCH`:

```bash
git tag -s v1.0.0 -m "QuickJSON 1.0.0"
git push origin v1.0.0
```

Il workflow calcola `versionCode = major × 1.000.000 + minor × 1.000 + patch`, esegue tutti i controlli, decodifica il keystore soltanto in `$RUNNER_TEMP`, compila con R8/resource shrinking, verifica firma e certificato con `apksigner` e pubblica APK, checksum SHA-256, mapping R8 e attestazione di provenienza nella GitHub Release.

## Licenza

[MIT](LICENSE) © 2026 xprss
