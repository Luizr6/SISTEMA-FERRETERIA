"""Bot de WhatsApp con IA (RF30): se simula la IA para probar el flujo sin internet."""
import json
from decimal import Decimal
from unittest import mock

from django.test import override_settings
from rest_framework.test import APITestCase

from asistente import ia
from productos.models import Categoria, Producto
from ventas.models import Pedido
from whatsapp import services
from whatsapp.models import Mensaje

IA_ON = {'API_KEY': 'test', 'MODELO': 'modelo-test', 'URL': 'http://ia.test', 'TIMEOUT': 5, 'HISTORIAL': 10}


def llamada(nombre, **args):
    return {'content': None, 'tool_calls': [{'id': f'call_{nombre}', 'type': 'function',
                                            'function': {'name': nombre, 'arguments': json.dumps(args)}}]}


def texto(t):
    return {'content': t}


@override_settings(IA=IA_ON)
class BotIATests(APITestCase):

    def setUp(self):
        cat = Categoria.objects.create(nombre='Fijaciones')
        self.tarugo = Producto.objects.create(codigo='TAR-001', nombre='Tarugo mariposa para drywall',
                                            categoria=cat, precio_compra=Decimal('0.20'),
                                            precio_venta=Decimal('0.50'), stock_actual=120)

    def _mensaje(self, t, telefono='51999111222'):
        _, salida = services.procesar_entrante(telefono=telefono, texto=t, nombre='Juan')
        return salida

    def test_entiende_lenguaje_natural_y_usa_precio_de_la_bd(self):
        vistos = []

        def falsa_ia(mensajes, herramientas=None, **kw):
            vistos.append(list(mensajes))  # copia: la lista crece en cada ronda
            if len(vistos) == 1:
                return llamada('buscar_productos', consulta='tarugo drywall')
            # la IA recibe el resultado REAL de la base de datos
            resultado = json.loads(mensajes[-1]['content'])
            item = resultado['resultados'][0]
            self.assertNotIn('stock', item)               # la IA no recibe cantidades de stock
            self.assertEqual(item['disponibilidad'], 'disponible')
            precio = item['precio_con_igv']
            return texto(f'Sí, tenemos **Tarugo mariposa** [TAR-001] a S/ {precio} c/u. ¿Cuántos necesita?')

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            salida = self._mensaje('oe causa tienes algo para colgar un cuadro en drywall?')
        self.assertEqual(salida.intencion, 'CONSULTA')
        self.assertIn('0.59', salida.texto)          # 0.50 + 18% IGV, salido de la BD
        self.assertNotIn('**', salida.texto)          # formato WhatsApp
        self.assertNotIn('TAR-001', salida.texto)     # el código interno no llega al cliente
        self.assertEqual(vistos[0][-1]['content'], 'oe causa tienes algo para colgar un cuadro en drywall?')

    def test_pedido_se_arma_y_solo_se_registra_cuando_el_cliente_confirma(self):
        # 1) el cliente pide: se arma el pedido, NO se registra
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('agregar_al_pedido', items=[{'codigo': 'TAR-001', 'cantidad': 20}]),
                texto('Le anoto 20 tarugos mariposa.')]):
            salida = self._mensaje('dame 20 de esos')
        self.assertEqual(Pedido.objects.count(), 0)
        self.assertIn('S/ 11.80', salida.texto)          # Django agrega el resumen si la IA no lo mostró
        self.assertIn('¿Desea que lo registre así', salida.texto)

        # 2) confirma: se registra, sin tocar stock, y el mensaje lo escribe Django (sin otra llamada a la IA)
        with mock.patch.object(ia, 'chat', side_effect=[llamada('confirmar_pedido')]) as m:
            salida = self._mensaje('sí, confírmelo por favor')
        self.assertEqual(m.call_count, 1)
        pedido = Pedido.objects.get()
        self.assertEqual((pedido.origen, pedido.estado), ('WHATSAPP', 'PENDIENTE'))
        self.assertIn(f'*{pedido.numero}*', salida.texto)
        self.assertIn('pago y el recojo son en tienda', salida.texto)
        self.tarugo.refresh_from_db()
        self.assertEqual(self.tarugo.stock_actual, 120)

        # 3) un "sí" repetido no duplica: el pedido armado ya se vació
        with mock.patch.object(ia, 'chat', side_effect=[llamada('confirmar_pedido'), texto('Ya quedó registrado.')]):
            self._mensaje('sí')
        self.assertEqual(Pedido.objects.count(), 1)

    def test_no_se_confirma_en_el_mismo_mensaje_que_se_arma(self):
        vistos = []

        def falsa_ia(mensajes, **kw):
            vistos.append(mensajes[-1])
            if len(vistos) == 1:
                return llamada('agregar_al_pedido', items=[{'codigo': 'TAR-001', 'cantidad': 5}])
            if len(vistos) == 2:
                return llamada('confirmar_pedido')
            return texto('Le anoto 5 tarugos, total S/ 2.95. ¿Desea que registre su pedido así?')

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            self._mensaje('quiero 5 tarugos')
        self.assertEqual(Pedido.objects.count(), 0)
        self.assertIn('todavía no confirmó', vistos[2]['content'])

    def test_no_confirma_si_el_cliente_pide_cambios(self):
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('agregar_al_pedido', items=[{'codigo': 'TAR-001', 'cantidad': 5}]), texto('Anotado.')]):
            self._mensaje('quiero 5 tarugos')
        with mock.patch.object(ia, 'chat', side_effect=[llamada('confirmar_pedido'), texto('¿Qué desea cambiar?')]):
            self._mensaje('aún no, quiero cambiar algo')
        self.assertEqual(Pedido.objects.count(), 0)

    def test_quitar_del_pedido(self):
        from whatsapp.models import Conversacion
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('agregar_al_pedido', items=[{'codigo': 'TAR-001', 'cantidad': 5}]), texto('Anotado.')]):
            self._mensaje('quiero 5 tarugos')
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('quitar_del_pedido', codigos=['TAR-001']), texto('Listo, lo quité.')]):
            self._mensaje('mejor no quiero los tarugos')
        self.assertEqual(Conversacion.objects.get().carrito['items'], {})

    def test_cliente_cancela_su_pedido_pendiente(self):
        from ventas import services as ventas_services
        pedido = ventas_services.crear_pedido(detalles=[{'producto': self.tarugo, 'cantidad': 2}], origen='WHATSAPP',
                                            contacto_telefono='51999111222')
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('cancelar_pedido', numero=pedido.numero), texto('Listo, su pedido quedó cancelado.')]):
            self._mensaje(f'ya no quiero el {pedido.numero}', telefono='51988000111')   # otro número: no puede
        pedido.refresh_from_db()
        self.assertEqual(pedido.estado, 'PENDIENTE')
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('cancelar_pedido', numero=pedido.numero), texto('Listo, su pedido quedó cancelado.')]):
            self._mensaje(f'ya no quiero el {pedido.numero}')
        pedido.refresh_from_db()
        self.assertEqual(pedido.estado, 'CANCELADO')

    def test_historial_se_envia_como_contexto(self):
        with mock.patch.object(ia, 'chat', side_effect=[texto('¡Hola! ¿En qué te ayudo?')]):
            self._mensaje('hola')
        capturado = {}

        def falsa_ia(mensajes, **kw):
            capturado['m'] = mensajes
            return texto('Claro.')

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            self._mensaje('y cuánto cuesta?')
        roles = [m['role'] for m in capturado['m']]
        self.assertIn('assistant', roles)             # incluye la respuesta anterior del bot
        self.assertEqual(capturado['m'][-1]['content'], 'y cuánto cuesta?')

    def test_si_la_ia_falla_responde_el_bot_por_reglas(self):
        with mock.patch.object(ia, 'chat', side_effect=ia.IAError('sin internet')):
            salida = self._mensaje('precio tarugo')
        self.assertEqual(salida.intencion, 'CONSULTA')
        self.assertIn('TAR-001', salida.texto)

    def test_si_la_ia_falla_mientras_arma_el_pedido_no_se_registra_nada(self):
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('agregar_al_pedido', items=[{'codigo': 'TAR-001', 'cantidad': 5}]), ia.IAError('cupo')]):
            salida = self._mensaje('quiero 5 tarugos')
        self.assertEqual(Pedido.objects.count(), 0)
        self.assertTrue(salida.texto)        # responde el bot por reglas

    def test_codigo_inexistente_no_se_agrega(self):
        vistos = []

        def falsa_ia(mensajes, **kw):
            vistos.append(mensajes[-1])
            if len(vistos) == 1:
                return llamada('agregar_al_pedido', items=[{'codigo': 'XXX-999', 'cantidad': 1}])
            return texto('No encontré ese producto, ¿me da más detalles?')

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            self._mensaje('quiero un XXX-999')
        self.assertEqual(Pedido.objects.count(), 0)
        self.assertIn('no existen', vistos[1]['content'])

    def test_estado_de_pedido_solo_del_mismo_numero(self):
        from ventas import services as ventas_services
        numero = ventas_services.crear_pedido(detalles=[{'producto': self.tarugo, 'cantidad': 2}], origen='WHATSAPP',
                                            contacto_telefono='51999111222').numero
        vistos = []

        def falsa_ia(mensajes, **kw):
            vistos.append(mensajes[-1])
            return llamada('consultar_pedido', numero=numero) if len(vistos) == 1 else texto('Listo.')

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            self._mensaje(f'estado {numero}', telefono='51988000111')  # otro número
        self.assertIn('No existe', vistos[1]['content'])

    def test_webhook_con_ia_responde_al_instante_y_procesa_aparte(self):
        payload = {'entry': [{'changes': [{'value': {'messages': [
            {'from': '51999111222', 'id': 'wamid.x', 'type': 'text', 'text': {'body': 'hola'}}]}}]}]}
        with mock.patch('whatsapp.views.threading.Thread') as hilo:
            r = self.client.post('/api/whatsapp/webhook/', payload, format='json')
        self.assertEqual(r.status_code, 200)
        hilo.return_value.start.assert_called_once()

    def test_estado_muestra_si_la_ia_esta_activa(self):
        from cuentas.models import Usuario
        self.client.force_authenticate(Usuario.objects.create_user('a', password='x', rol='ADMIN'))
        r = self.client.get('/api/whatsapp/estado/')
        self.assertTrue(r.data['ia']['activa'])


class SinIATests(APITestCase):
    """Sin GROQ_API_KEY todo sigue como antes (bot por reglas)."""

    @override_settings(IA={**IA_ON, 'API_KEY': ''})
    def test_sin_clave_no_llama_a_la_ia(self):
        cat = Categoria.objects.create(nombre='Herramientas')
        Producto.objects.create(codigo='MAR-001', nombre='Martillo', categoria=cat,
                                precio_compra=10, precio_venta=Decimal('32.00'), stock_actual=5)
        with mock.patch.object(ia, 'chat') as llamada_ia:
            _, salida = services.procesar_entrante(telefono='51999', texto='precio martillo')
        llamada_ia.assert_not_called()
        self.assertIn('MAR-001', salida.texto)
        self.assertEqual(Mensaje.objects.filter(direccion='SALIENTE').count(), 1)


@override_settings(IA=IA_ON)
class RespuestasDegeneradasTests(APITestCase):
    """gpt-oss puede degenerar ("………", inglés mezclado): nunca se envía eso al cliente."""

    def setUp(self):
        cat = Categoria.objects.create(nombre='Medición')
        Producto.objects.create(codigo='CIN-001', nombre='Cinta métrica 5 m', categoria=cat,
                                precio_compra=10, precio_venta=Decimal('22.00'), stock_actual=8)

    def test_texto_valido(self):
        self.assertTrue(ia.texto_valido('Listo, su pedido *P-000008* quedó registrado. Total S/ 25.96.'))
        self.assertFalse(ia.texto_valido('Listo, su pedido … ……… … …… Probably ……… It looks …...…...'))
        self.assertFalse(ia.texto_valido('Okay, we need to answer the user'))
        self.assertFalse(ia.texto_valido(''))
        self.assertFalse(ia.texto_valido('• Cinta métrica 5 m: S/ 25.96, disponible¿ desea ?'))
        self.assertTrue(ia.texto_valido('• *Cinta métrica 5 m*: S/ 25.96\n¿Cuántas unidades necesita?'))

    def test_respuesta_trabada_se_vuelve_a_pedir(self):
        with mock.patch.object(ia, 'chat', side_effect=[
                texto('Cinta métrica: S/ 25.96, disponible¿ desea ?'),
                texto('Tenemos *Cinta métrica 5 m* a S/ 25.96. ¿Cuántas unidades necesita?')]) as m:
            _, salida = services.procesar_entrante(telefono='51902', texto='tiene guincha?')
        self.assertEqual(m.call_count, 2)
        self.assertIn('¿Cuántas unidades necesita?', salida.texto)

    def test_confirmacion_la_escribe_django_y_no_la_ia(self):
        with mock.patch.object(ia, 'chat', side_effect=[
                llamada('agregar_al_pedido', items=[{'codigo': 'CIN-001', 'cantidad': 1}]), texto('Anotado.')]):
            services.procesar_entrante(telefono='51900', texto='solo 1', nombre='Ana')
        basura = texto('Listo, su pedido P-1 quedó quedado ......?')
        with mock.patch.object(ia, 'chat', side_effect=[llamada('confirmar_pedido'), basura]):
            _, salida = services.procesar_entrante(telefono='51900', texto='si por favor', nombre='Ana')
        pedido = Pedido.objects.get()
        self.assertIn(f'*{pedido.numero}*', salida.texto)
        self.assertIn('pago y el recojo son en tienda', salida.texto)
        self.assertNotIn('…', salida.texto)
        self.assertNotIn('CIN-001', salida.texto)

    def test_historial_no_reenvia_respuestas_danadas(self):
        with mock.patch.object(ia, 'chat', side_effect=[texto('Okay … ……… ……… Probably ……')]):
            services.procesar_entrante(telefono='51901', texto='hola')
        capturado = {}

        def falsa_ia(mensajes, **kw):
            capturado['m'] = mensajes
            return texto('Buenas noches, ¿en qué le puedo ayudar?')

        with mock.patch.object(ia, 'chat', side_effect=falsa_ia):
            services.procesar_entrante(telefono='51901', texto='buenas noches')
        self.assertFalse(any('Probably' in (m.get('content') or '') for m in capturado['m']))

    def test_parametros_recomendados_para_gpt_oss(self):
        enviado = {}

        class Resp:
            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def read(self):
                return json.dumps({'choices': [{'message': {'content': 'Hola'}}]}).encode()

        def falso_urlopen(req, timeout=None):
            enviado['cuerpo'] = json.loads(req.data)
            return Resp()

        with override_settings(IA={**IA_ON, 'MODELO': 'openai/gpt-oss-120b'}), \
                mock.patch('asistente.ia.urllib.request.urlopen', side_effect=falso_urlopen):
            ia.chat([{'role': 'user', 'content': 'hola'}], temperatura=0.1)
        c = enviado['cuerpo']
        self.assertTrue(0.5 <= c['temperature'] <= 0.7)
        self.assertEqual(c['reasoning_effort'], 'low')
        self.assertIs(c['include_reasoning'], False)


class EliminarConversacionesTests(APITestCase):
    """El Administrador puede eliminar chats o mensajes del panel; el vendedor no. Los pedidos se conservan."""

    def setUp(self):
        from cuentas.models import Usuario
        from ventas.models import Pedido
        from whatsapp.models import Conversacion, Mensaje
        self.admin = Usuario.objects.create_user('adm_wa', password='x', rol='ADMIN')
        self.vend = Usuario.objects.create_user('ven_wa', password='x', rol='VENDEDOR')
        self.conv = Conversacion.objects.create(telefono='51911222333', nombre='Prueba')
        self.pedido = Pedido.objects.create(origen='WHATSAPP', contacto_nombre='Prueba', contacto_telefono='51911222333')
        self.m1 = Mensaje.objects.create(conversacion=self.conv, direccion='ENTRANTE', texto='hola')
        self.m2 = Mensaje.objects.create(conversacion=self.conv, direccion='SALIENTE', texto='¡Hola!', pedido=self.pedido)

    def test_vendedor_no_puede_eliminar(self):
        self.client.force_authenticate(self.vend)
        base = f'/api/whatsapp/conversaciones/{self.conv.pk}/'
        self.assertEqual(self.client.delete(base).status_code, 403)
        self.assertEqual(self.client.delete(f'{base}mensajes/{self.m1.pk}/').status_code, 403)

    def test_admin_elimina_un_mensaje(self):
        from whatsapp.models import Mensaje
        self.client.force_authenticate(self.admin)
        r = self.client.delete(f'/api/whatsapp/conversaciones/{self.conv.pk}/mensajes/{self.m1.pk}/')
        self.assertEqual(r.status_code, 204)
        self.assertFalse(Mensaje.objects.filter(pk=self.m1.pk).exists())
        self.assertTrue(Mensaje.objects.filter(pk=self.m2.pk).exists())
        # el historial sigue funcionando
        self.assertEqual(len(self.client.get(f'/api/whatsapp/conversaciones/{self.conv.pk}/mensajes/').data), 1)

    def test_admin_elimina_conversacion_y_se_conserva_el_pedido(self):
        from auditoria.models import Bitacora
        from ventas.models import Pedido
        from whatsapp.models import Conversacion, Mensaje
        self.client.force_authenticate(self.admin)
        r = self.client.delete(f'/api/whatsapp/conversaciones/{self.conv.pk}/')
        self.assertEqual(r.status_code, 204)
        self.assertFalse(Conversacion.objects.filter(pk=self.conv.pk).exists())
        self.assertEqual(Mensaje.objects.count(), 0)
        self.assertTrue(Pedido.objects.filter(pk=self.pedido.pk).exists())
        self.assertTrue(Bitacora.objects.filter(accion='ELIMINAR', modelo='Conversacion').exists())


class LimiteGratuitoTests(APITestCase):
    """Groq gratis limita tokens por minuto: ante un 429 se usa el modelo de respaldo, no el bot por reglas."""

    def setUp(self):
        ia._pausado_hasta.clear()

    def tearDown(self):
        ia._pausado_hasta.clear()

    def _urlopen(self, modelos_vistos, limitados, espera='30'):
        import io
        import urllib.error

        class Resp:
            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

            def read(self):
                return json.dumps({'choices': [{'message': {'content': 'Hola, ¿qué busca?'}}]}).encode()

        def falso(req, timeout=None):
            modelo = json.loads(req.data)['model']
            modelos_vistos.append(modelo)
            if modelo in limitados:
                raise urllib.error.HTTPError(req.full_url, 429, 'Too Many Requests',
                                            {'retry-after': espera}, io.BytesIO(b'rate limit'))
            return Resp()
        return falso

    def test_429_pasa_al_modelo_de_respaldo_y_lo_recuerda(self):
        vistos = []
        cfg = {**IA_ON, 'MODELO': 'principal', 'MODELO_RESPALDO': 'respaldo'}
        with override_settings(IA=cfg), \
                mock.patch('asistente.ia.urllib.request.urlopen', side_effect=self._urlopen(vistos, {'principal'})):
            self.assertEqual(ia.chat([{'role': 'user', 'content': 'hola'}])['content'], 'Hola, ¿qué busca?')
            self.assertEqual(vistos, ['principal', 'respaldo'])
            ia.chat([{'role': 'user', 'content': 'hola'}])
        self.assertEqual(vistos[-1], 'respaldo')
        self.assertEqual(vistos.count('principal'), 1)   # en pausa: no se le insiste

    def test_si_todos_estan_en_limite_espera_unos_segundos_y_reintenta(self):
        vistos = []
        limitados = {'principal'}
        falso = self._urlopen(vistos, limitados, espera='0.1')

        def una_vez(req, timeout=None):
            r = falso(req, timeout)
            return r
        cfg = {**IA_ON, 'MODELO': 'principal', 'MODELO_RESPALDO': ''}

        def lado(req, timeout=None):
            if len(vistos) == 1:
                limitados.clear()
            return una_vez(req, timeout)
        with override_settings(IA=cfg), mock.patch('asistente.ia.time.sleep'), \
                mock.patch('asistente.ia.urllib.request.urlopen', side_effect=lado):
            ia.chat([{'role': 'user', 'content': 'hola'}])
        self.assertEqual(vistos, ['principal', 'principal'])

    def test_si_todos_estan_limitados_lanza_error_para_usar_el_bot_por_reglas(self):
        cfg = {**IA_ON, 'MODELO': 'principal', 'MODELO_RESPALDO': 'respaldo'}
        with override_settings(IA=cfg), mock.patch(
                'asistente.ia.urllib.request.urlopen', side_effect=self._urlopen([], {'principal', 'respaldo'})):
            with self.assertRaises(ia.IAError):
                ia.chat([{'role': 'user', 'content': 'hola'}])

    def test_texto_cortado_o_repetido_se_descarta(self):
        self.assertFalse(ia.texto_valido('• *Esm1: Esmalte sintético negro negro ... (continúa…).'))
        self.assertTrue(ia.texto_valido('Tenemos *Cemento Sol 42.5 kg*: S/ 31.00. ¿Cuántas bolsas necesita?'))
