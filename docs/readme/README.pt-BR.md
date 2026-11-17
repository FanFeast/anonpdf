# AnonPDF

[English](../../README.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · **Português** · [Русский](README.ru.md) · [中文](README.zh-CN.md)

Um leitor e editor de PDF que trabalha no seu dispositivo e em nenhum outro lugar.

**Sem anúncios. Sem conta. Sem rastreamento. Sem versão paga. Sem permissão de rede.**

O AnonPDF existe porque todos os outros apps de PDF parecem querer uma assinatura,
uma conta na nuvem e uma olhada nos seus documentos. Este não quer nada disso. Ele
não consegue enviar seus arquivos porque não tem permissão para acessar a rede — não
é uma promessa numa política de privacidade, é uma regra imposta pelo sistema
operacional.

> Esta tradução foi feita com ajuda automática. A versão de referência é o
> [README em inglês](../../README.md); correções são bem-vindas.

## Baixar

- **Android:** o APK da [versão mais recente](https://github.com/FanFeast/anonpdf/releases/latest). Em breve no Google Play.
- **Linux:** `sudo snap install anonpdf` pela [Snap Store](https://snapcraft.io/anonpdf), ou o arquivo portátil
  `AnonPDF-…-linux-x86_64.tar.gz` da [versão mais recente](https://github.com/FanFeast/anonpdf/releases/latest):
  extraia e dê dois cliques em `AnonPDF.AppImage`. Nada é instalado.
- **Windows:** o `AnonPDF-…-windows-x64.zip` portátil da [versão mais recente](https://github.com/FanFeast/anonpdf/releases/latest):
  extraia e abra `AnonPDF.exe`.

O app para computador é o mesmo motor e o mesmo editor numa janela nativa, com a
mesma regra: sem acesso à rede.

| Biblioteca | Leitura | Seus arquivos |
|---|---|---|
| ![Início](../screenshots/home.png) | ![Visualizador](../screenshots/viewer.png) | ![Navegar](../screenshots/browse.png) |

## O editor

É aqui que quase tudo acontece. Escolha páginas, acumule quantas alterações quiser,
veja o resultado real se atualizar e grave o arquivo uma única vez no final. Nada é
gravado até você salvar, e o original nunca é modificado.

| Editar | Arrastar para reordenar | Recolher as ferramentas |
|---|---|---|
| ![Editor](../screenshots/editor.png) | ![Reordenar](../screenshots/editor-reorder.png) | ![Recolhido](../screenshots/editor-collapsed.png) |

| Cobrir e reescrever | Destacar e desenhar | Juntar e continuar editando |
|---|---|---|
| ![Texto](../screenshots/editor-text.png) | ![Desenho](../screenshots/editor-draw.png) | ![Juntar](../screenshots/editor-merge.png) |

- **Todas as ferramentas ficam aqui**, em quatro páginas: *Páginas*, *Conteúdo*,
  *Arquivo inteiro* e *Converter e proteger*. As de arquivo inteiro recebem o
  documento com suas alterações pendentes já aplicadas.
- **Preencha formulários PDF**: caixas de texto, caixas de seleção, botões de opção
  e listas, com prévia enquanto você digita e a opção de travar as respostas na
  página ao salvar.
- **Juntar insere onde você mandar.** As páginas de outro PDF entram como páginas
  comuns, que você pode girar, reordenar, cortar ou excluir.
- **Edite o conteúdo, não só as páginas.** Cubra algo com corretivo e escreva seu
  texto por cima, destaque um trecho ou desenhe à mão livre. O corretivo remove de
  verdade o que cobre: a página vira imagem sob a caixa, e o texto escondido não
  pode mais ser copiado nem pesquisado.
- **Reordene arrastando** e **selecione várias páginas com toques**.
- **Desfaça e refaça** todo o histórico.

A prévia não é uma aproximação: ela é gerada pelo mesmo código que grava o arquivo
final, e um teste confere que os dois batem pixel a pixel.

## A garantia, e como conferir

O AnonPDF declara exatamente **uma** permissão:

```
android.permission.MANAGE_EXTERNAL_STORAGE
```

Ela permite que o navegador embutido liste os PDFs do seu dispositivo. É
**opcional**: recuse e tudo continua funcionando pelo seletor do sistema, que não
precisa de permissão. O Android a trata como "acesso especial do app", então ela
não aparece na página normal de Permissões do app.

No que você pode confiar:

- **Sem permissão `INTERNET`.** O sistema recusa qualquer conexão. Não há servidor,
  telemetria, relatório de falhas nem rede de anúncios. Os testes conferem que
  abrir uma conexão e resolver um nome realmente falham.
- **Sem bibliotecas de análise ou anúncios.** Só AndroidX, Kotlin e PdfBox-Android.
- **`allowBackup=false`**, então os documentos também não saem por backup na nuvem.
- **Um teste quebra o build** se qualquer outra permissão aparecer.

Confira você mesmo:

```bash
apkanalyzer manifest permissions app-release.apk
```

### O que ele guarda

| O quê | Onde | Como apagar |
|---|---|---|
| Nomes de até 20 arquivos recentes | Armazenamento privado do app, fora do backup | Configurações → Limpar arquivos recentes |
| Cópias de trabalho do arquivo em edição | Cache do app | Configurações → Excluir arquivos temporários; também a cada abertura |
| Três preferências de exibição | Armazenamento privado do app | Desinstalar |

Essa é a lista completa. Nenhum identificador de nenhum tipo.

## Recursos

**Leitura:** rolagem contínua, zoom com dois dedos, ir para página, busca de texto,
modo noturno, PDFs com senha.

**Encontrar arquivos:** navegador embutido com todos os PDFs do dispositivo, por
pastas, ou o seletor do sistema.

**Edição** (tudo de uma vez, com prévia): girar, cortar, redimensionar (A3, A4, A5,
Carta, Ofício, Tabloide), excluir, reordenar, juntar, preencher formulários, texto,
corretivo, destaque, desenho, marca-d'água e números de página.

**Arquivos inteiros:** juntar, dividir, extrair páginas, comprimir, PDF em JPG/PNG,
imagens em PDF, extrair texto, proteger com AES-256 ou remover uma senha, e assinar.

**Ao salvar:** se quiser, remova as informações ocultas do PDF (autor, app que o
criou, datas, metadados XMP, arquivos anexados, scripts e nomes nos comentários).

**Idiomas:** inglês, alemão, espanhol, francês, hindi, português do Brasil, russo e
chinês simplificado, conforme o idioma do dispositivo.

## O que fica de fora, de propósito

- **Conversão para Word, Excel ou PowerPoint.** Fazer direito exige um motor de
  escritório completo.
- **OCR.** Ler texto de digitalizações exige um modelo grande ou um servidor.
- **Sincronização, contas, links de compartilhamento.** A ideia é que nada saia.
- **Formulários XFA**, um formato que só os apps da Adobe preenchem.
- **Assinaturas digitais criptográficas.** "Assinar PDF" coloca uma imagem da sua
  assinatura: tinta no papel, não um certificado.

## Requisitos

- Android 12 ou mais recente; cerca de 15 MB instalado
- Linux x86-64 ou Windows de 64 bits para a versão de computador

## Compilação, testes e arquitetura

Veja o [README em inglês](../../README.md#building).

## Licença

MIT. Veja [LICENSE](../../LICENSE).
