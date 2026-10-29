"""
FER-49 / RF20 - Comprobante interno de venta (boleta simplificada) en PDF.
No es un comprobante electrónico SUNAT (exclusión explícita del alcance).
"""
import io

from django.conf import settings
from django.utils import timezone
from reportlab.lib import colors
from reportlab.lib.pagesizes import A5
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

UNIDADES = ['', 'UNO', 'DOS', 'TRES', 'CUATRO', 'CINCO', 'SEIS', 'SIETE', 'OCHO', 'NUEVE', 'DIEZ',
            'ONCE', 'DOCE', 'TRECE', 'CATORCE', 'QUINCE', 'DIECISÉIS', 'DIECISIETE', 'DIECIOCHO',
            'DIECINUEVE', 'VEINTE', 'VEINTIUNO', 'VEINTIDÓS', 'VEINTITRÉS', 'VEINTICUATRO',
            'VEINTICINCO', 'VEINTISÉIS', 'VEINTISIETE', 'VEINTIOCHO', 'VEINTINUEVE']
DECENAS = ['', '', '', 'TREINTA', 'CUARENTA', 'CINCUENTA', 'SESENTA', 'SETENTA', 'OCHENTA', 'NOVENTA']
CENTENAS = ['', 'CIENTO', 'DOSCIENTOS', 'TRESCIENTOS', 'CUATROCIENTOS', 'QUINIENTOS', 'SEISCIENTOS',
            'SETECIENTOS', 'OCHOCIENTOS', 'NOVECIENTOS']


def _menor_mil(n):
    if n == 0:
        return ''
    if n == 100:
        return 'CIEN'
    c, resto = divmod(n, 100)
    partes = [CENTENAS[c]] if c else []
    if resto < 30:
        partes.append(UNIDADES[resto])
    else:
        d, u = divmod(resto, 10)
        partes.append(DECENAS[d] + (f' Y {UNIDADES[u]}' if u else ''))
    return ' '.join(p for p in partes if p)


def numero_a_letras(valor):
    """82.60 -> 'OCHENTA Y DOS CON 60/100 SOLES'"""
    entero = int(valor)
    centimos = int(round((valor - entero) * 100))
    if entero == 0:
        texto = 'CERO'
    else:
        millones, resto = divmod(entero, 1_000_000)
        miles, unidades = divmod(resto, 1000)
        partes = []
        if millones:
            partes.append('UN MILLÓN' if millones == 1 else f'{_menor_mil(millones)} MILLONES')
        if miles:
            partes.append('MIL' if miles == 1 else f'{_menor_mil(miles)} MIL')
        if unidades:
            partes.append(_menor_mil(unidades))
        texto = ' '.join(partes)
    texto = texto.replace('VEINTIUNO MIL', 'VEINTIÚN MIL').replace('UNO MIL', 'UN MIL')
    return f'{texto} CON {centimos:02d}/100 SOLES'


def _s(valor):
    return f'S/ {valor:,.2f}'


def generar_pdf_venta(venta):
    negocio = settings.FERRETERIA
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A5, leftMargin=10 * mm, rightMargin=10 * mm,
                            topMargin=10 * mm, bottomMargin=10 * mm,
                            title=f'Comprobante {venta.numero}', author=negocio['NOMBRE'])
    est = getSampleStyleSheet()
    normal = ParagraphStyle('n', parent=est['Normal'], fontSize=8, leading=10)
    pequeno = ParagraphStyle('p', parent=normal, fontSize=7, leading=9, textColor=colors.HexColor('#555555'))
    titulo = ParagraphStyle('t', parent=est['Title'], fontSize=13, leading=16, spaceAfter=0, alignment=0)
    centro = ParagraphStyle('c', parent=normal, alignment=1)

    azul = colors.HexColor('#1F3A5F')
    gris = colors.HexColor('#E9EDF2')
    ancho = A5[0] - 20 * mm

    # Cabecera: datos del negocio a la izquierda, recuadro del comprobante a la derecha
    datos_negocio = [
        Paragraph(f'<b>{negocio["NOMBRE"]}</b>', titulo),
        Paragraph(negocio['DIRECCION'], normal),
        Paragraph(f'Tel.: {negocio["TELEFONO"]}', normal),
    ]
    recuadro = Table([
        [Paragraph(f'<b>RUC {negocio["RUC"]}</b>', centro)],
        [Paragraph('<b>BOLETA DE VENTA<br/>INTERNA</b>', centro)],
        [Paragraph(f'<b>{venta.numero}</b>', centro)],
    ], colWidths=[48 * mm])
    recuadro.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, azul),
        ('BACKGROUND', (0, 1), (-1, 1), gris),
        ('TOPPADDING', (0, 0), (-1, -1), 3), ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    cabecera = Table([[datos_negocio, recuadro]], colWidths=[ancho - 50 * mm, 50 * mm])
    cabecera.setStyle(TableStyle([('VALIGN', (0, 0), (-1, -1), 'TOP'),
                                  ('LEFTPADDING', (0, 0), (-1, -1), 0)]))

    fecha = timezone.localtime(venta.fecha)
    cliente = venta.cliente
    info = Table([
        [Paragraph('<b>Fecha:</b>', normal), Paragraph(fecha.strftime('%d/%m/%Y %H:%M'), normal),
         Paragraph('<b>Vendedor:</b>', normal), Paragraph(venta.vendedor.get_full_name() or venta.vendedor.username, normal)],
        [Paragraph('<b>Cliente:</b>', normal), Paragraph(cliente.nombre if cliente else 'CLIENTES VARIOS', normal),
         Paragraph(f'<b>{cliente.tipo_documento if cliente else "Doc."}:</b>', normal),
         Paragraph((cliente.numero_documento or '-') if cliente else '-', normal)],
        [Paragraph('<b>Pago:</b>', normal), Paragraph(venta.get_metodo_pago_display(), normal),
         Paragraph('<b>Origen:</b>', normal), Paragraph(venta.get_origen_display(), normal)],
    ], colWidths=[17 * mm, ancho / 2 - 17 * mm, 18 * mm, ancho / 2 - 18 * mm])
    info.setStyle(TableStyle([('LEFTPADDING', (0, 0), (-1, -1), 0), ('TOPPADDING', (0, 0), (-1, -1), 1),
                              ('BOTTOMPADDING', (0, 0), (-1, -1), 1)]))

    filas = [['Cant.', 'Und.', 'Descripción', 'P. Unit.', 'Importe']]
    for d in venta.detalles.select_related('producto'):
        filas.append([str(d.cantidad), d.producto.unidad_medida,
                      Paragraph(f'{d.producto.nombre}<br/><font size=6 color="#777777">{d.producto.codigo}</font>', normal),
                      f'{d.precio_unitario:,.2f}', f'{d.subtotal:,.2f}'])
    detalle = Table(filas, colWidths=[11 * mm, 11 * mm, ancho - 58 * mm, 18 * mm, 18 * mm], repeatRows=1)
    detalle.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), azul), ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'), ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('ALIGN', (0, 0), (1, -1), 'CENTER'), ('ALIGN', (3, 0), (-1, -1), 'RIGHT'),
        ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ('LINEBELOW', (0, 1), (-1, -1), 0.25, colors.HexColor('#CCCCCC')),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#F7F9FB')]),
    ]))

    totales = Table([
        ['Op. gravada', _s(venta.subtotal)],
        [f'IGV ({int(float(settings.IGV_TASA) * 100)}%)', _s(venta.igv)],
        ['TOTAL', _s(venta.total)],
    ], colWidths=[30 * mm, 26 * mm], hAlign='RIGHT')
    totales.setStyle(TableStyle([
        ('ALIGN', (0, 0), (-1, -1), 'RIGHT'), ('FONTSIZE', (0, 0), (-1, -1), 8),
        ('FONTNAME', (0, 2), (-1, 2), 'Helvetica-Bold'), ('FONTSIZE', (0, 2), (-1, 2), 10),
        ('LINEABOVE', (0, 2), (-1, 2), 1, azul), ('TEXTCOLOR', (0, 2), (-1, 2), azul),
    ]))

    elementos = [cabecera, Spacer(1, 5 * mm), info, Spacer(1, 4 * mm), detalle, Spacer(1, 3 * mm), totales,
                 Spacer(1, 2 * mm), Paragraph(f'<b>SON:</b> {numero_a_letras(venta.total)}', normal)]
    if venta.observacion:
        elementos += [Spacer(1, 2 * mm), Paragraph(f'<b>Obs.:</b> {venta.observacion}', normal)]
    if venta.estado == 'ANULADA':
        elementos += [Spacer(1, 4 * mm), Paragraph(
            f'<font color="#B00020"><b>COMPROBANTE ANULADO</b> el '
            f'{timezone.localtime(venta.fecha_anulacion):%d/%m/%Y %H:%M} - {venta.motivo_anulacion}</font>', normal)]
    elementos += [Spacer(1, 6 * mm), Paragraph(
        'Comprobante interno de control. No válido como comprobante de pago electrónico ante SUNAT.', pequeno),
        Paragraph('¡Gracias por su compra!', pequeno)]

    doc.build(elementos)
    return buffer.getvalue()
