# Guía: WhatsApp real + IA (FERRETERÍA C&R)

Funciona en tres niveles. Cada uno es útil por sí solo:

| Nivel | Qué necesitas | Qué obtienes |
|---|---|---|
| 1. IA en el simulador | Clave de Groq | El bot entiende lenguaje natural en el simulador del panel web |
| 2. WhatsApp real | + cuenta de Meta + ngrok | Los clientes escriben al número de WhatsApp y el bot responde |
| 3. Asistente del Admin | Clave de Groq | `/api/asistente/` y `/api/asistente/alertas/` para la app móvil |

> **Importante:** la IA nunca inventa precios ni stock. Django los saca de la base de datos y la IA
> solo entiende al cliente y redacta. Si la IA falla o no hay clave, responde el bot por reglas.

---

## Paso 1. Clave de la IA (Groq) — 5 minutos, gratis

1. Entra a **https://console.groq.com** y crea tu cuenta (con Google o correo).
2. Menú **API Keys** → **Create API Key** → ponle un nombre (ej. `ferreteria`) → copia la clave
   (empieza con `gsk_`). Solo se muestra una vez.
3. Abre el archivo **`.env`** del backend y agrega al final:

   ```
   # --- IA (Groq) ---
   GROQ_API_KEY=gsk_pega_aqui_tu_clave
   GROQ_MODEL=openai/gpt-oss-120b
   FERRETERIA_HORARIO=Lunes a sábado de 8:00 a. m. a 7:00 p. m.
   ```

4. Reinicia Django (`Ctrl + C` y otra vez `python manage.py runserver`).
5. Comprueba: entra a `http://localhost:8000/api/whatsapp/estado/` con sesión de admin (o desde
   Swagger en `/api/docs/`). Debe decir `"ia": {"activa": true, ...}`.
6. Prueba en el **simulador de WhatsApp del panel web** con mensajes naturales:
   - `oe causa, ¿tienes algo para colgar un cuadro?`
   - `¿a cuánto el cemento?` → y luego `dame 3`
   - `¿a qué hora atienden?`

**No compartas la clave** (no la subas a GitHub ni la pongas en la app). El `.env` ya está en `.gitignore`.

**Sobre el modelo:** Groq retiró los modelos Llama del plan gratuito en agosto de 2026. Por eso se usa
`openai/gpt-oss-120b` (modelo abierto, gratis en Groq). Si quieren probar el modelo de Meta que Groq
tiene en vista previa, cambien `GROQ_MODEL=meta-models/muse-spark-open` (puede cambiar o retirarse).

---

## Paso 2. Exponer tu PC a internet con ngrok — 10 minutos, gratis

Meta solo envía mensajes a una dirección **pública con HTTPS**; `localhost` no le sirve.

1. Crea una cuenta en **https://ngrok.com** y descarga ngrok para Windows.
2. En el panel de ngrok copia tu *authtoken* y ejecuta una sola vez:
   ```
   ngrok config add-authtoken TU_AUTHTOKEN
   ```
3. Con Django corriendo en el puerto 8000, abre otra terminal y ejecuta:
   ```
   ngrok http 8000
   ```
   Si tu cuenta tiene un **dominio fijo gratis** (menú *Domains*), úsalo para no tener que cambiar
   la URL en Meta cada vez:
   ```
   ngrok http --url=tu-dominio.ngrok-free.app 8000
   ```
4. Copia la dirección `https://....ngrok-free.app`. Tu webhook será:
   ```
   https://....ngrok-free.app/api/whatsapp/webhook/
   ```
   (con la barra `/` al final).

Mientras ngrok y Django estén abiertos, WhatsApp funciona. Si cierras la PC, el bot deja de responder.

---

## Paso 3. Cuenta de Meta (WhatsApp Cloud API) — 20 minutos, gratis

1. Entra a **https://developers.facebook.com** con tu Facebook → **Mis apps** → **Crear app**.
2. Caso de uso: **Otro** → tipo **Empresa (Business)** → ponle nombre (ej. `Ferreteria CR Bot`).
3. En la app, busca el producto **WhatsApp** → **Configurar**. Meta crea un **número de prueba** gratis.
4. En **WhatsApp → Configuración de la API** verás:
   - **Token de acceso temporal** (dura 24 h).
   - **Identificador del número de teléfono** (*Phone Number ID*).
   - **Para:** agrega aquí tu celular (y los de tu equipo, hasta 5). Meta te envía un código para
     verificar cada número. **Solo esos números pueden chatear con el número de prueba.**
5. En **Configuración de la app → Básica** copia la **Clave secreta de la app**.
6. Agrega al `.env`:
   ```
   WHATSAPP_TOKEN=EAAG...el_token
   WHATSAPP_PHONE_NUMBER_ID=1234567890
   WHATSAPP_APP_SECRET=la_clave_secreta
   WHATSAPP_VERIFY_TOKEN=ferreteria-verify-2026
   ```
   y reinicia Django.
7. En **WhatsApp → Configuración → Webhook** → **Editar**:
   - **URL de devolución de llamada:** `https://....ngrok-free.app/api/whatsapp/webhook/`
   - **Token de verificación:** el mismo `WHATSAPP_VERIFY_TOKEN` del `.env`.
   - **Verificar y guardar.**
8. En la misma sección, en **Campos del webhook**, suscríbete a **messages**.
9. Desde tu celular (uno de los verificados) escribe **"hola"** al número de prueba de Meta.
   El bot responde, y la conversación aparece en el panel web (sección WhatsApp).

### Token que no vence (para que no falle el día de la exposición)
El token temporal dura 24 h. Para uno permanente:
**business.facebook.com → Configuración del negocio → Usuarios del sistema → Agregar** (rol Admin)
→ **Asignar activos** (tu app) → **Generar token** con los permisos `whatsapp_business_messaging` y
`whatsapp_business_management` → reemplaza `WHATSAPP_TOKEN` en el `.env`.

---

## Paso 4. Asistente del Administrador (para la app móvil)

Con la clave de Groq ya funciona (requiere iniciar sesión como **ADMIN**):

| Endpoint | Para qué |
|---|---|
| `GET /api/asistente/` | Resumen del negocio de hoy redactado por la IA |
| `POST /api/asistente/` `{"pregunta": "¿qué me falta comprar?"}` | Responde con datos reales |
| `GET /api/asistente/alertas/` | Avisos para las notificaciones (stock agotado/mínimo, pedidos por confirmar, compras atrasadas, ventas bajas) |

Puedes probarlos en Swagger: `http://localhost:8000/api/docs/`.

---

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| Meta dice "No se pudo validar la URL" | Django o ngrok apagados, falta la `/` final, o el token de verificación no coincide con el `.env`. |
| El mensaje llega al panel pero no al celular | Error **131030**: tu número no está en la lista "Para" del número de prueba. Agrégalo y verifícalo. |
| Error **190** / "token expired" | Venció el token temporal (24 h). Genera otro o usa el token permanente. |
| El bot responde "Esto encontré 🔎..." (estilo rígido) | Está usando el bot por reglas: falta `GROQ_API_KEY`, la clave es inválida o se agotó el cupo gratis (error 429). Revisa la consola de Django. |
| Cambié el `.env` y no pasa nada | Hay que reiniciar `runserver`. |
| La URL de ngrok cambió | Sin dominio fijo, ngrok cambia la URL al reiniciar: actualízala en el webhook de Meta. |
