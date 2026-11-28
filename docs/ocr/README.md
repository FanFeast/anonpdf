# OCR language packs

AnonPDF reads scanned pages with [Tesseract](https://github.com/tesseract-ocr/tesseract),
which is built into the app. The languages are not: each one is a `.traineddata`
file you add yourself, from the editor's **Read text** tool or from **Recognise text
(OCR)**. AnonPDF never downloads anything, so a pack only arrives because you
handed it over.

## Where to get them

Every [release](https://github.com/FanFeast/anonpdf/releases) carries these packs
as downloads:

| File | Language |
|---|---|
| `eng.traineddata` | English |
| `deu.traineddata` | German |
| `spa.traineddata` | Spanish |
| `fra.traineddata` | French |
| `hin.traineddata` | Hindi |
| `por.traineddata` | Portuguese |
| `rus.traineddata` | Russian |
| `chi_sim.traineddata` | Chinese (simplified) |
| `chi_tra.traineddata` | Chinese (traditional) |
| `ita.traineddata` | Italian |
| `nld.traineddata` | Dutch |
| `pol.traineddata` | Polish |
| `tur.traineddata` | Turkish |
| `ukr.traineddata` | Ukrainian |
| `jpn.traineddata` | Japanese |
| `kor.traineddata` | Korean |
| `ara.traineddata` | Arabic |

They are Tesseract's own `tessdata_fast` models, unchanged: the release job fetches
them from [tesseract-ocr/tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast)
at a fixed commit and checks each one against [`language-packs.sha256`](language-packs.sha256)
before attaching it. Any other `.traineddata` file from that collection, or from
`tessdata` / `tessdata_best` (slower, a little more accurate), works too.

The models are © Google and the Tesseract contributors, under the Apache License 2.0.

## Adding one

1. Download the file for each language you need. Keep the name as it is: the name
   says which language it is.
2. On the phone or computer, open **Recognise text (OCR)** and tap
   **Add a language pack…**, then pick the file. Several at once is fine.
3. Tick the languages a document is written in, and run it.

A pack is checked before it is kept, and it is only ever read as data.
