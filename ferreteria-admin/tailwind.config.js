/** @type {import('tailwindcss').Config} */
// Colores definidos como variables CSS (src/index.css): cada token tiene versión clara y oscura,
// así el modo oscuro funciona en todo el panel con solo poner la clase `dark` en <html>.
const v = (nombre) => `rgb(var(--${nombre}) / <alpha-value>)`
const escala = (nombre, pasos) => Object.fromEntries(pasos.map((p) => [p, v(`${nombre}-${p}`)]))

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Geist', 'Inter', 'system-ui', 'sans-serif'],
        display: ['Geist', 'Inter', 'system-ui', 'sans-serif'],
      },
      colors: {
        fondo: v('fondo'),           // fondo de la página
        superficie: v('superficie'), // tarjetas, tablas, modales
        lateral: v('lateral'),       // barra lateral
        solido: v('solido'),         // bloques oscuros (botón secundario fuerte)
        gris: escala('gris', [50, 100, 200, 300, 400, 500, 600, 700, 800, 900]),
        acento: escala('acento', [50, 100, 200, 400, 500, 600, 700]),   // azul corporativo
        exito: escala('exito', [50, 100, 500, 600, 700]),              // verde esmeralda
        aviso: escala('aviso', [50, 100, 200, 500, 600, 700]),         // ámbar sobrio
        peligro: escala('peligro', [50, 100, 500, 600, 700]),          // rojo
      },
      boxShadow: {
        panel: '0 1px 2px 0 rgb(15 23 42 / 0.04)',
        flota: '0 20px 50px -12px rgb(15 23 42 / 0.35)',
      },
      borderRadius: { panel: '12px' },
    },
  },
  plugins: [],
}
