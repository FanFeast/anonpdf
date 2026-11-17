# AnonPDF

[English](../../README.md) · **Deutsch** · [Español](README.es.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Português](README.pt-BR.md) · [Русский](README.ru.md) · [中文](README.zh-CN.md)

Ein PDF-Reader und -Editor, der auf deinem Gerät arbeitet und nirgendwo sonst.

**Keine Werbung. Kein Konto. Kein Tracking. Keine Bezahlversion. Keine Netzwerkberechtigung.**

AnonPDF gibt es, weil scheinbar jede andere PDF-App ein Abo, ein Cloud-Konto und
einen Blick in deine Dokumente will. Diese will nichts davon. Sie kann deine Dateien
nicht hochladen, weil sie keine Berechtigung für das Netzwerk hat – das ist kein
Versprechen in einer Datenschutzerklärung, sondern wird vom Betriebssystem
durchgesetzt.

> Diese Übersetzung entstand mit maschineller Hilfe. Maßgeblich ist die
> [englische README](../../README.md); Korrekturen sind willkommen.

## Herunterladen

- **Android:** die APK aus dem [neuesten Release](https://github.com/FanFeast/anonpdf/releases/latest). Ein Eintrag bei Google Play folgt.
- **Linux:** `sudo snap install anonpdf` aus dem [Snap Store](https://snapcraft.io/anonpdf) oder die portable Datei
  `AnonPDF-…-linux-x86_64.tar.gz` aus dem [neuesten Release](https://github.com/FanFeast/anonpdf/releases/latest):
  entpacken und `AnonPDF.AppImage` doppelklicken. Es wird nichts installiert.
- **Windows:** das portable `AnonPDF-…-windows-x64.zip` aus dem [neuesten Release](https://github.com/FanFeast/anonpdf/releases/latest):
  entpacken und `AnonPDF.exe` starten.

Die Desktop-App ist dieselbe Engine und derselbe Editor in einem nativen Fenster,
mit derselben Regel: kein Netzwerkzugriff.

| Bibliothek | Lesen | Deine Dateien |
|---|---|---|
| ![Start](../screenshots/home.png) | ![Viewer](../screenshots/viewer.png) | ![Durchsuchen](../screenshots/browse.png) |

## Der Editor

Hier passiert das meiste. Seiten auswählen, beliebig viele Änderungen sammeln, dem
echten Ergebnis beim Aktualisieren zusehen und die Datei am Ende einmal schreiben.
Bis zum Speichern wird nichts auf den Datenträger geschrieben, und das Original
bleibt unverändert.

| Bearbeiten | Ziehen zum Umsortieren | Werkzeuge einklappen |
|---|---|---|
| ![Editor](../screenshots/editor.png) | ![Umsortieren](../screenshots/editor-reorder.png) | ![Eingeklappt](../screenshots/editor-collapsed.png) |

| Abdecken und neu schreiben | Markieren und zeichnen | Einfügen und weiter bearbeiten |
|---|---|---|
| ![Text](../screenshots/editor-text.png) | ![Zeichnen](../screenshots/editor-draw.png) | ![Zusammenführen](../screenshots/editor-merge.png) |

- **Alle Werkzeuge an einem Ort**, auf vier Seiten: *Seiten*, *Inhalt*,
  *Ganze Datei* und *Umwandeln & sperren*. Die Werkzeuge für ganze Dateien
  bekommen das Dokument mit deinen ausstehenden Änderungen.
- **PDF-Formulare ausfüllen**: Textfelder, Kontrollkästchen, Optionsfelder und
  Auswahllisten, mit Vorschau beim Tippen und der Option, die Antworten beim
  Speichern festzuschreiben.
- **Zusammenführen fügt dort ein, wo du willst.** Die Seiten eines anderen PDFs
  kommen als normale Seiten hinzu, die du drehen, sortieren, zuschneiden oder
  löschen kannst.
- **Den Inhalt bearbeiten, nicht nur die Seiten.** Etwas abdecken und eigenen
  Text darüberschreiben, eine Stelle markieren oder frei zeichnen. Die Abdeckung
  entfernt wirklich, was sie verdeckt: Die Seite wird unter dem Kasten zum Bild,
  der verdeckte Text lässt sich danach weder kopieren noch durchsuchen.
- **Umsortieren durch Ziehen** und **Mehrfachauswahl durch Tippen**.
- **Rückgängig und Wiederholen** über den ganzen Verlauf.

Die Vorschau ist keine Annäherung: Sie entsteht durch denselben Code, der die
fertige Datei schreibt, und ein Test prüft, dass beide pixelgenau übereinstimmen.

## Die Garantie, und wie du sie prüfst

AnonPDF fordert genau **eine** Berechtigung an:

```
android.permission.MANAGE_EXTERNAL_STORAGE
```

Damit kann der eingebaute Browser die PDFs auf deinem Gerät auflisten. Sie ist
**optional**: Lehnst du sie ab, funktioniert alles über die Dateiauswahl des
Systems, die keine Berechtigung braucht. Android führt sie als „speziellen
App-Zugriff“, daher steht sie nicht auf der normalen Berechtigungsseite der App.

Worauf du dich verlassen kannst:

- **Keine `INTERNET`-Berechtigung.** Das System lehnt jede Verbindung ab. Kein
  Server, keine Telemetrie, keine Absturzberichte, kein Werbenetzwerk. Tests
  prüfen, dass Verbindungsaufbau und Namensauflösung wirklich scheitern.
- **Keine Analyse- oder Werbebibliotheken.** Nur AndroidX, Kotlin und
  PdfBox-Android.
- **`allowBackup=false`**, Dokumente verlassen das Gerät also auch nicht über
  Cloud-Backups.
- **Ein Test bricht den Build ab**, wenn irgendeine andere Berechtigung auftaucht.

Selbst prüfen:

```bash
apkanalyzer manifest permissions app-release.apk
```

### Was gespeichert wird

| Was | Wo | Wie entfernen |
|---|---|---|
| Namen von bis zu 20 zuletzt geöffneten Dateien | Privater App-Speicher, ohne Backup | Einstellungen → Zuletzt geöffnete Dateien löschen |
| Arbeitskopien der bearbeiteten Datei | App-Cache | Einstellungen → Temporäre Dateien löschen; auch bei jedem Start |
| Drei Anzeigeeinstellungen | Privater App-Speicher | Deinstallieren |

Das ist die vollständige Liste. Keinerlei Kennung.

## Funktionen

**Lesen:** durchgehendes Scrollen, Zoomen mit zwei Fingern, zu Seite springen,
Textsuche, Nachtmodus, passwortgeschützte PDFs.

**Dateien finden:** eingebauter Browser mit allen PDFs auf dem Gerät, nach Ordnern,
oder die Systemauswahl.

**Bearbeiten** (alles in einem Durchgang, mit Vorschau): drehen, zuschneiden,
Größe ändern (A3, A4, A5, Letter, Legal, Tabloid), löschen, umsortieren,
zusammenführen, Formulare ausfüllen, Text, Abdeckung, Markierung, Zeichnung,
Wasserzeichen und Seitenzahlen.

**Ganze Dateien:** zusammenführen, teilen, Seiten entnehmen, verkleinern, PDF zu
JPG/PNG, Bilder zu PDF, Text entnehmen, mit AES-256 schützen oder ein Passwort
entfernen, und signieren.

**Beim Speichern:** auf Wunsch die versteckten Informationen des PDFs entfernen
(Autor, erstellende App, Daten, XMP-Metadaten, angehängte Dateien, Skripte und
Namen in Kommentaren).

**Sprachen:** Englisch, Deutsch, Spanisch, Französisch, Hindi, brasilianisches
Portugiesisch, Russisch und vereinfachtes Chinesisch, je nach Gerätesprache.

## Bewusst nicht enthalten

- **Umwandlung in Word, Excel oder PowerPoint.** Richtig gemacht braucht das eine
  komplette Office-Engine.
- **OCR.** Text aus Scans zu lesen braucht ein großes Modell oder einen Server.
- **Synchronisierung, Konten, Freigabelinks.** Der Sinn ist, dass nichts das Gerät verlässt.
- **XFA-Formulare**, ein Format, das nur Adobes Apps ausfüllen.
- **Kryptografische digitale Signaturen.** „PDF signieren“ setzt ein Bild deiner
  Unterschrift: Tinte auf Papier, kein Zertifikat.

## Voraussetzungen

- Android 12 oder neuer; etwa 15 MB installiert
- Linux x86-64 oder 64-Bit-Windows für die Desktop-Version

## Bauen, Tests und Architektur

Siehe die [englische README](../../README.md#building).

## Lizenz

MIT. Siehe [LICENSE](../../LICENSE).
