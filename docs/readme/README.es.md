# AnonPDF

[English](../../README.md) · [Deutsch](README.de.md) · **Español** · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Português](README.pt-BR.md) · [Русский](README.ru.md) · [中文](README.zh-CN.md)

Un lector y editor de PDF que trabaja en tu dispositivo y en ningún otro sitio.

**Sin anuncios. Sin cuentas. Sin rastreo. Sin versión de pago. Sin permiso de red.**

AnonPDF existe porque todas las demás apps de PDF parecen querer una suscripción,
una cuenta en la nube y echar un vistazo a tus documentos. Esta no quiere nada de
eso. No puede subir tus archivos porque no tiene permiso para acceder a la red: no
es una promesa en una política de privacidad, es algo que impone el sistema
operativo.

> Esta traducción se escribió con ayuda automática. La versión de referencia es el
> [README en inglés](../../README.md); las correcciones son bienvenidas.

## Descargar

- **Android:** el APK de la [última versión](https://github.com/FanFeast/anonpdf/releases/latest). Pronto estará en Google Play.
- **Linux:** `sudo snap install anonpdf` desde la [Snap Store](https://snapcraft.io/anonpdf), o el archivo portable
  `AnonPDF-…-linux-x86_64.tar.gz` de la [última versión](https://github.com/FanFeast/anonpdf/releases/latest):
  descomprímelo y haz doble clic en `AnonPDF.AppImage`. No se instala nada.
- **Windows:** el `AnonPDF-…-windows-x64.zip` portable de la [última versión](https://github.com/FanFeast/anonpdf/releases/latest):
  descomprímelo y ejecuta `AnonPDF.exe`.

La app de escritorio es el mismo motor y el mismo editor en una ventana nativa, con
la misma regla: sin acceso a la red.

| Biblioteca | Lectura | Tus archivos |
|---|---|---|
| ![Inicio](../screenshots/home.png) | ![Visor](../screenshots/viewer.png) | ![Explorar](../screenshots/browse.png) |

## El editor

Aquí ocurre casi todo. Elige páginas, acumula todos los cambios que quieras, mira
cómo se actualiza el resultado real y escribe el archivo una sola vez al final.
Nada toca el disco hasta que guardas, y el original nunca se modifica.

| Editar | Arrastrar para reordenar | Plegar las herramientas |
|---|---|---|
| ![Editor](../screenshots/editor.png) | ![Reordenar](../screenshots/editor-reorder.png) | ![Plegado](../screenshots/editor-collapsed.png) |

| Tapar y reescribir | Resaltar y dibujar | Unir y seguir editando |
|---|---|---|
| ![Texto](../screenshots/editor-text.png) | ![Dibujo](../screenshots/editor-draw.png) | ![Unir](../screenshots/editor-merge.png) |

- **Todas las herramientas están aquí**, en cuatro páginas: *Páginas*,
  *Contenido*, *Todo el archivo* y *Convertir y proteger*. Las de archivo
  completo reciben el documento con tus cambios pendientes ya aplicados.
- **Rellena formularios PDF**: cuadros de texto, casillas, botones de opción y
  listas, con vista previa mientras escribes y la opción de bloquear las
  respuestas en la página al guardar.
- **Unir inserta donde tú digas.** Las páginas de otro PDF entran como páginas
  normales que puedes girar, reordenar, recortar o eliminar.
- **Edita el contenido, no solo las páginas.** Tapa algo con corrector y escribe
  tu texto encima, resalta un fragmento o dibuja a mano alzada. El corrector
  elimina de verdad lo que cubre: la página se convierte en imagen bajo el
  cuadro, así que el texto oculto no se puede copiar ni buscar después.
- **Reordena arrastrando** y **selecciona varias páginas tocando**.
- **Deshacer y rehacer** todo el historial.

La vista previa no es una aproximación: la genera el mismo código que escribe el
archivo final, y una prueba comprueba que coinciden píxel a píxel.

## La garantía, y cómo comprobarla

AnonPDF declara exactamente **un** permiso:

```
android.permission.MANAGE_EXTERNAL_STORAGE
```

Permite que el explorador integrado liste los PDF de tu dispositivo. Es
**opcional**: si lo rechazas, todo sigue funcionando con el selector del sistema,
que no necesita ningún permiso. Android lo trata como un «acceso especial de
aplicaciones», así que no aparece en la página normal de Permisos de la app.

En qué puedes confiar:

- **Sin permiso `INTERNET`.** El sistema rechaza cualquier conexión. No hay
  servidor, ni telemetría, ni informes de errores, ni red publicitaria. Las
  pruebas comprueban que abrir una conexión y resolver un nombre fallan de verdad.
- **Sin dependencias de analítica ni publicidad.** Solo AndroidX, Kotlin y
  PdfBox-Android.
- **`allowBackup=false`**, así que los documentos tampoco salen por copias de
  seguridad en la nube.
- **Una prueba que rompe la compilación** si aparece cualquier otro permiso.

Compruébalo tú mismo:

```bash
apkanalyzer manifest permissions app-release.apk
```

### Lo que guarda

| Qué | Dónde | Cómo borrarlo |
|---|---|---|
| Nombres de hasta 20 archivos recientes | Almacenamiento privado, fuera de las copias | Ajustes → Borrar archivos recientes |
| Copias de trabajo del archivo que editas | Caché de la app | Ajustes → Eliminar archivos temporales; también al abrir la app |
| Tres preferencias de visualización | Almacenamiento privado | Desinstalar |

Esa es la lista completa. Ningún identificador de ningún tipo.

## Funciones

**Lectura:** desplazamiento continuo, zoom con dos dedos, ir a una página, buscar
texto, modo nocturno, PDF con contraseña.

**Encontrar archivos:** explorador integrado con todos los PDF del dispositivo,
por carpetas, o el selector del sistema.

**Editar** (todo en una pasada, con vista previa): girar, recortar, cambiar de
tamaño (A3, A4, A5, Carta, Oficio, Tabloide), eliminar, reordenar, unir, rellenar
formularios, texto, corrector, resaltado, dibujo, marca de agua y números de
página.

**Archivos completos:** unir, dividir, extraer páginas, comprimir, PDF a JPG/PNG,
imágenes a PDF, extraer texto, proteger con AES-256 o quitar una contraseña, y
firmar.

**Al guardar:** opcionalmente, quitar la información oculta del PDF (autor, app
con la que se creó, fechas, metadatos XMP, archivos adjuntos, scripts y nombres
en los comentarios).

**Idiomas:** inglés, alemán, español, francés, hindi, portugués de Brasil, ruso y
chino simplificado, según el idioma del dispositivo.

## Lo que no incluye, a propósito

- **Conversión a Word, Excel o PowerPoint.** Hacerla bien exige un motor ofimático
  completo.
- **OCR.** Leer texto de imágenes escaneadas necesita un modelo grande o un servidor.
- **Sincronización, cuentas, enlaces para compartir.** La idea es que nada salga.
- **Formularios XFA**, un formato que solo rellenan las apps de Adobe.
- **Firmas digitales criptográficas.** «Firmar PDF» coloca una imagen de tu firma:
  es tinta sobre papel, no un certificado.

## Requisitos

- Android 12 o posterior; unos 15 MB instalada
- Linux x86-64 o Windows de 64 bits para la versión de escritorio

## Compilar, pruebas y arquitectura

Consulta el [README en inglés](../../README.md#building).

## Licencia

MIT. Consulta [LICENSE](../../LICENSE).
