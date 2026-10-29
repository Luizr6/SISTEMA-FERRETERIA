"""RF11 - Carga masiva de productos desde CSV."""
import csv
import io
from decimal import Decimal, InvalidOperation

from django.db import transaction

from .models import Categoria, Marca, MovimientoInventario, Producto, Proveedor

COLUMNAS = ['codigo', 'nombre', 'categoria', 'marca', 'proveedor_ruc', 'unidad_medida',
            'precio_compra', 'precio_venta', 'stock_actual', 'stock_minimo', 'descripcion']
OBLIGATORIAS = ['codigo', 'nombre', 'categoria', 'precio_compra', 'precio_venta']
UNIDADES = {u for u, _ in Producto.UNIDADES}

PLANTILLA = (
    ','.join(COLUMNAS) + '\n'
    'MAR-002,Martillo de bola 24 oz,Herramientas manuales,Stanley,20123456789,UND,25.00,42.00,10,3,Mango de fibra\n'
    'PIN-002,Pintura esmalte rojo 1/4 gl,Pinturas,CPP,,GLN,15.50,26.90,8,2,\n'
)


def _decimal(valor, campo):
    try:
        d = Decimal(str(valor).replace(',', '.').strip())
    except (InvalidOperation, ValueError):
        raise ValueError(f'{campo} "{valor}" no es un número.')
    if d < 0:
        raise ValueError(f'{campo} no puede ser negativo.')
    return d


def _entero(valor, campo, defecto=0):
    if valor in (None, ''):
        return defecto
    try:
        n = int(str(valor).strip())
    except ValueError:
        raise ValueError(f'{campo} "{valor}" no es un entero.')
    if n < 0:
        raise ValueError(f'{campo} no puede ser negativo.')
    return n


def importar_csv(archivo, *, usuario=None, actualizar=False):
    """
    Devuelve {'creados': n, 'actualizados': n, 'errores': [{'fila': 3, 'error': '...'}]}.
    Cada fila se procesa en su propio savepoint: una fila mala no anula las demás.
    Acepta separador coma o punto y coma (Excel en español suele usar ';').
    """
    contenido = archivo.read()
    if isinstance(contenido, bytes):
        try:
            contenido = contenido.decode('utf-8-sig')
        except UnicodeDecodeError:
            contenido = contenido.decode('latin-1')

    primera = contenido.splitlines()[0] if contenido.strip() else ''
    separador = ';' if primera.count(';') > primera.count(',') else ','
    lector = csv.DictReader(io.StringIO(contenido), delimiter=separador)
    if not lector.fieldnames:
        return {'creados': 0, 'actualizados': 0, 'errores': [{'fila': 1, 'error': 'El archivo está vacío.'}]}
    lector.fieldnames = [c.strip().lower() for c in lector.fieldnames]
    faltan = [c for c in OBLIGATORIAS if c not in lector.fieldnames]
    if faltan:
        return {'creados': 0, 'actualizados': 0,
                'errores': [{'fila': 1, 'error': f'Faltan columnas obligatorias: {", ".join(faltan)}'}]}

    creados = actualizados = 0
    errores = []
    for n, fila in enumerate(lector, start=2):  # fila 1 = cabecera
        fila = {k: (v or '').strip() for k, v in fila.items() if k}
        if not any(fila.values()):
            continue
        try:
            with transaction.atomic():
                for c in OBLIGATORIAS:
                    if not fila.get(c):
                        raise ValueError(f'"{c}" es obligatorio.')
                codigo = fila['codigo'].upper()
                categoria, _ = Categoria.objects.get_or_create(nombre=fila['categoria'])
                marca = Marca.objects.get_or_create(nombre=fila['marca'])[0] if fila.get('marca') else None
                proveedor = None
                if fila.get('proveedor_ruc'):
                    proveedor = Proveedor.objects.filter(ruc=fila['proveedor_ruc']).first()
                    if proveedor is None:
                        raise ValueError(f'No existe proveedor con RUC {fila["proveedor_ruc"]}.')
                unidad = (fila.get('unidad_medida') or 'UND').upper()
                if unidad not in UNIDADES:
                    raise ValueError(f'Unidad "{unidad}" no válida. Usa: {", ".join(sorted(UNIDADES))}.')
                datos = dict(
                    nombre=fila['nombre'], categoria=categoria, marca=marca, proveedor=proveedor,
                    unidad_medida=unidad, descripcion=fila.get('descripcion') or None,
                    precio_compra=_decimal(fila['precio_compra'], 'precio_compra'),
                    precio_venta=_decimal(fila['precio_venta'], 'precio_venta'),
                    stock_minimo=_entero(fila.get('stock_minimo'), 'stock_minimo', 5),
                )
                stock = _entero(fila.get('stock_actual'), 'stock_actual', 0)

                existente = Producto.objects.filter(codigo=codigo).first()
                if existente:
                    if not actualizar:
                        raise ValueError(f'El código {codigo} ya existe (marca "actualizar" para sobrescribir).')
                    for k, v in datos.items():
                        setattr(existente, k, v)
                    diferencia = stock - existente.stock_actual
                    existente.stock_actual = stock
                    existente.save()
                    if diferencia:
                        MovimientoInventario.objects.create(
                            producto=existente, tipo='AJUSTE', cantidad=diferencia,
                            stock_resultante=stock, motivo='Ajuste por carga CSV', usuario=usuario)
                    actualizados += 1
                else:
                    p = Producto.objects.create(codigo=codigo, stock_actual=stock, **datos)
                    if stock:
                        MovimientoInventario.objects.create(
                            producto=p, tipo='INGRESO', cantidad=stock, stock_resultante=stock,
                            motivo='Stock inicial (carga CSV)', usuario=usuario)
                    creados += 1
        except ValueError as e:
            errores.append({'fila': n, 'error': str(e)})
        except Exception as e:  # noqa: BLE001
            errores.append({'fila': n, 'error': f'Error inesperado: {e}'})

    return {'creados': creados, 'actualizados': actualizados, 'errores': errores}
