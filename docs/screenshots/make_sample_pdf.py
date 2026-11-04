#!/usr/bin/env python3
"""Writes sample-report.pdf, the document shown in the screenshots.

Eight pages: page four is stored rotated a quarter turn and page eight is
landscape, so the page tools have something uneven to work on. Standard library
only; the PDF uses the built-in Helvetica fonts, so nothing is embedded.

    python3 docs/screenshots/make_sample_pdf.py [out.pdf]
"""
import sys

A4 = (595, 842)
MARGIN = 60

INTRO = [
    [
        "AnonPDF handles this file entirely on your device. Nothing here was",
        "uploaded, and nothing about it was reported anywhere. The app has no",
        "internet permission, so it could not have done either.",
    ],
    [
        "Use this document to try the tools: split it, pull pages out, reorder",
        "them, stamp a watermark across it, add page numbers, or lock it with a",
        "password and then unlock it again.",
    ],
    [
        "Page four is deliberately stored rotated a quarter turn, and page eight",
        "is landscape rather than portrait, so you can check that the page tools",
        "cope with a document that is not uniform.",
    ],
]

PAGES = [
    ("AnonPDF sample report", A4, 0),
    ("Reading", A4, 0),
    ("Organising pages", A4, 0),
    ("This page is stored rotated", A4, 90),
    ("Converting", A4, 0),
    ("Markup", A4, 0),
    ("Security", A4, 0),
    ("A landscape page", (A4[1], A4[0]), 0),
]


def text(s: str) -> str:
    return "(" + s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"


def content(title: str, size: tuple[int, int]) -> bytes:
    w, h = size
    right = w - MARGIN
    y = h - 72
    ops = ["BT /F2 20 Tf", f"{MARGIN} {y} Td {text(title)} Tj ET"]
    y -= 12
    ops += ["0.11 0.42 0.87 RG 1.5 w", f"{MARGIN} {y} m {right} {y} l S"]
    y -= 26
    for para in INTRO:
        for line in para:
            ops.append(f"BT /F1 9 Tf {MARGIN} {y} Td {text(line)} Tj ET")
            y -= 13
        y -= 14
    y -= 20
    ops += ["0.89 0.92 0.98 rg", f"{MARGIN} {y - 72} {right - MARGIN} 72 re f"]
    ops += ["0.85 0.2 0.2 RG 2.5 w", f"{MARGIN} 60 m {right} 60 l S"]
    return "\n".join(ops).encode("latin-1")


def build() -> bytes:
    objects: list[bytes] = []

    def add(body: bytes) -> int:
        objects.append(body)
        return len(objects)

    catalog = add(b"")
    pages = add(b"")
    f1 = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    f2 = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
    kids = []
    for title, size, rotate in PAGES:
        stream = content(title, size)
        c = add(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
        kids.append(add(
            b"<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %d %d] /Rotate %d "
            b"/Resources << /Font << /F1 %d 0 R /F2 %d 0 R >> >> /Contents %d 0 R >>"
            % (pages, size[0], size[1], rotate, f1, f2, c)
        ))
    objects[catalog - 1] = b"<< /Type /Catalog /Pages %d 0 R >>" % pages
    objects[pages - 1] = b"<< /Type /Pages /Kids [%s] /Count %d >>" % (
        b" ".join(b"%d 0 R" % k for k in kids), len(kids))
    info = add(b"<< /Title (AnonPDF sample report) /Producer (make_sample_pdf.py) >>")

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, body in enumerate(objects, 1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objects) + 1)
    out += b"".join(b"%010d 00000 n \n" % o for o in offsets)
    out += b"trailer\n<< /Size %d /Root %d 0 R /Info %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (
        len(objects) + 1, catalog, info, xref)
    return bytes(out)


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "sample-report.pdf"
    with open(path, "wb") as f:
        f.write(build())
