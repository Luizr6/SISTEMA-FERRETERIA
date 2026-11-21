"""
Páginas públicas que Meta exige para publicar la app de WhatsApp:
  /privacidad/          Política de privacidad
  /eliminacion-datos/   Instrucciones para eliminar los datos del usuario

Se generan con los datos del negocio de settings.FERRETERIA (nombre, RUC, dirección, teléfono),
así que no hay que mantener un HTML aparte.
"""
from html import escape

from django.conf import settings
from django.http import HttpResponse
from django.utils import timezone

ESTILO = """
:root { --navy:#0F1E3D; --ambar:#F59E0B; --texto:#1f2937; --suave:#6b7280; --fondo:#f6f7f9; }
* { box-sizing:border-box; }
body { margin:0; font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
       color:var(--texto); background:var(--fondo); line-height:1.6; }
header { background:var(--navy); color:#fff; padding:28px 16px; }
header .marca { max-width:820px; margin:0 auto; display:flex; gap:14px; align-items:center; }
header .logo { width:44px; height:44px; border-radius:10px; background:var(--ambar);
               display:grid; place-items:center; color:var(--navy); font-weight:800; }
header h1 { margin:0; font-size:20px; letter-spacing:.3px; }
header p { margin:0; color:#b6c2dc; font-size:14px; }
main { max-width:820px; margin:24px auto; background:#fff; border-radius:14px; padding:28px 24px;
       box-shadow:0 1px 3px rgba(0,0,0,.06); }
h2 { color:var(--navy); font-size:22px; margin-top:0; }
h3 { color:var(--navy); font-size:16px; margin:24px 0 6px; }
ul { padding-left:20px; }
.nota { color:var(--suave); font-size:13px; }
footer { text-align:center; color:var(--suave); font-size:13px; padding:8px 16px 32px; }
"""


def _pagina(titulo, cuerpo):
    t = settings.FERRETERIA
    nombre = escape(t['NOMBRE'])
    html = f"""<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{escape(titulo)} · {nombre}</title><style>{ESTILO}</style></head>
<body>
<header><div class="marca"><div class="logo">C&amp;R</div>
<div><h1>{nombre}</h1><p>Sistema de gestión · Atención por WhatsApp</p></div></div></header>
<main>{cuerpo}</main>
<footer>{nombre} · RUC {escape(t['RUC'])} · {escape(t['DIRECCION'])} · Tel. {escape(t['TELEFONO'])}</footer>
</body></html>"""
    return HttpResponse(html)


def politica_privacidad(request):
    t = settings.FERRETERIA
    nombre = escape(t['NOMBRE'])
    hoy = timezone.localdate().strftime('%d/%m/%Y')
    return _pagina('Política de privacidad', f"""
<h2>Política de privacidad</h2>
<p class="nota">Última actualización: {hoy}</p>
<p>En {nombre} (RUC {escape(t['RUC'])}) respetamos su privacidad. Esta política explica qué datos
recogemos cuando nos escribe por WhatsApp o compra en nuestra tienda, para qué los usamos y cómo puede
ejercer sus derechos, de acuerdo con la Ley N.° 29733, Ley de Protección de Datos Personales del Perú,
y su reglamento.</p>

<h3>1. Datos que recogemos</h3>
<ul>
  <li>Su número de WhatsApp y el nombre de perfil que usted tiene configurado.</li>
  <li>Los mensajes que nos envía (consultas de productos, precios y pedidos).</li>
  <li>Los pedidos que registra: productos, cantidades y montos.</li>
  <li>Si compra en tienda y lo solicita: nombre, DNI o RUC y teléfono para el comprobante.</li>
</ul>

<h3>2. Para qué usamos sus datos</h3>
<ul>
  <li>Responder sus consultas sobre productos, precios y disponibilidad.</li>
  <li>Registrar sus pedidos y que un asesor se los confirme.</li>
  <li>Emitir los comprobantes de sus compras.</li>
  <li>Llevar el registro interno de ventas e inventario de la tienda.</li>
</ul>
<p>No vendemos ni alquilamos sus datos, y no los usamos para publicidad de terceros.</p>

<h3>3. Asistente automático</h3>
<p>Nuestro WhatsApp es atendido por un asistente automático que usa inteligencia artificial para
entender su mensaje. Para ello, el texto de su consulta se procesa a través de un proveedor de
inteligencia artificial. Los precios, el stock y los pedidos provienen siempre de nuestro propio sistema.
Un asesor de la tienda revisa y confirma cada pedido.</p>

<h3>4. Con quién compartimos sus datos</h3>
<ul>
  <li><strong>Meta Platforms (WhatsApp)</strong>, que transmite los mensajes.</li>
  <li><strong>El proveedor de inteligencia artificial</strong>, solo para procesar el texto de su consulta.</li>
  <li>Autoridades competentes, únicamente cuando la ley lo exija.</li>
</ul>

<h3>5. Cuánto tiempo los guardamos</h3>
<p>Conservamos las conversaciones y pedidos mientras sean necesarios para atenderle y para el registro
de ventas. Los datos de los comprobantes se conservan por el plazo que exigen las normas tributarias.</p>

<h3>6. Sus derechos</h3>
<p>Puede solicitar el acceso, la rectificación, la cancelación (eliminación) o la oposición al uso de sus
datos. Vea cómo hacerlo en <a href="/eliminacion-datos/">Eliminación de datos</a>.</p>

<h3>7. Contacto</h3>
<p>{nombre} · {escape(t['DIRECCION'])} · Teléfono / WhatsApp: {escape(t['TELEFONO'])}<br>
Horario: {escape(t['HORARIO'])}</p>
""")


def eliminacion_datos(request):
    t = settings.FERRETERIA
    nombre = escape(t['NOMBRE'])
    return _pagina('Eliminación de datos', f"""
<h2>Cómo solicitar la eliminación de sus datos</h2>
<p>Usted puede pedir en cualquier momento que {nombre} elimine sus conversaciones de WhatsApp y los
datos personales asociados a su número.</p>

<h3>Opción 1: por WhatsApp</h3>
<p>Escríbanos al mismo número de WhatsApp de la tienda el mensaje: <strong>ELIMINAR MIS DATOS</strong>.
Un asesor confirmará su solicitud.</p>

<h3>Opción 2: en tienda</h3>
<p>Acérquese a {escape(t['DIRECCION'])} en nuestro horario de atención ({escape(t['HORARIO'])})
o llámenos al {escape(t['TELEFONO'])}.</p>

<h3>Qué se elimina y en cuánto tiempo</h3>
<ul>
  <li>Sus conversaciones de WhatsApp con la tienda y sus datos de contacto.</li>
  <li>Atendemos la solicitud en un plazo máximo de 10 días hábiles y le confirmamos por el mismo medio.</li>
</ul>
<p class="nota">Los comprobantes de compras ya emitidas se conservan solo por el plazo que exigen las
normas tributarias, y después se eliminan.</p>
<p><a href="/privacidad/">Ver la política de privacidad</a></p>
""")
