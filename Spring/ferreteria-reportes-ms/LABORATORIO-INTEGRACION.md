# Laboratorio de pruebas de integración del sistema de ferretería

## Objetivo

Comprobar la integración entre la API REST del microservicio Spring Boot, su seguridad JWT/API key, los servicios, los repositorios JPA, una base de prueba y el generador de PDF.

Repositorio de referencia: https://github.com/Luizr6/SISTEMA-FERRETERIA

Versión base: `9ce517208f6a6aa86adb12ac04ebdd9ee209b4bb` de la rama `main`.

## Adaptación de la guía

La guía GLAB-S12 presenta seis ejercicios sobre Vet, Owner, Specialty, Visit, Types y Vet_specialties en PetClinic. Esas entidades no existen en este proyecto. Esta entrega adapta el objetivo de construir, ejecutar y documentar pruebas de integración a seis funcionalidades existentes de la ferretería. El laboratorio se desarrolla sobre el proyecto integrador de ferretería, según la indicación del equipo. Los nombres de PetClinic se conservan únicamente como referencia de la guía original.

Spring no procesa compras, ventas ni cambios de stock. Django es el núcleo transaccional. Por ello se prueban consultas de negocio, reportes y registro de auditoría; no se agregan controladores CRUD ficticios ni escrituras sobre el inventario.

## Componentes y casos

| Ejercicio adaptado | Clase | Comprobaciones principales |
|---|---|---|
| 1 Ventas | VentasControllerTest | Totales e IGV; exclusión de anulaciones; límites del día en Lima; relaciones y agrupaciones; día vacío; fecha inválida |
| 2 Inventario | StockCriticoControllerTest | Agotados y mínimo; exclusión de inactivos; costo y cantidad de reposición; proveedor opcional; consulta sin alterar stock; lista vacía |
| 3 Registro de auditoría | RegistroAuditoriaControllerTest | API key; persistencia de JSON; quinto fallo crítico; separación de usuarios y ventana temporal; validación; JWT de vendedor |
| 4 Consulta de auditoría | ConsultaAuditoriaControllerTest | Consulta por ID y 404; filtros y paginación; resumen; tipos; fechas en Lima |
| 5 Exportación PDF | PdfControllerTest | PDF legible con datos; evento REPORTE_EXPORTADO; stock sin cambios; denegación sin efectos |
| 6 Seguridad y salud | SeguridadControllerTest | 401 sin JWT; 403 para vendedor; firma, expiración y refresh; límites de API key; salud pública con consulta real |

Las pruebas existentes de JwtDjangoServiceTest son unitarias y se conservan por separado.

## Diseño de las pruebas

Se utiliza `@SpringBootTest` con `@AutoConfigureMockMvc`: las solicitudes recorren Spring MVC y los componentes reales. No hay mocks de servicios ni repositorios. MockMvc no abre un puerto HTTP externo.

`@ActiveProfiles("test")` carga exclusivamente la configuración de prueba H2 en memoria, en modo PostgreSQL. Hibernate crea las tablas de prueba; no utiliza el esquema de trabajo de Django. La dependencia H2 tiene alcance `test` y no se incorpora al servicio de producción.

`@Sql` carga datos sintéticos antes de cada caso y `@Transactional` revierte los cambios al terminar. Los identificadores generados de auditoría se leen de la respuesta y no se supone que comiencen en uno. Los JWT se firman localmente con claims compatibles con SimpleJWT y claves ficticias.

## Ejecución

Requisitos: JDK 17 o superior y Maven 3.9 o superior. La primera ejecución necesita acceso a Maven Central para descargar dependencias.

Desde `Spring/ferreteria-reportes-ms`:

```powershell
mvn test
```

También se puede ejecutar `Ejecutar-Laboratorio.ps1`, indicando `-Maven` si Maven no está en PATH. En IntelliJ IDEA, abrir el `pom.xml`, seleccionar un JDK compatible y ejecutar el objetivo Maven `test`.

Para ejecutar solo los seis grupos nuevos:

```powershell
mvn "-Dtest=*ControllerTest" test
```

El perfil se activa en las anotaciones de las pruebas, por lo que no es necesario configurar variables de la base productiva, iniciar Django ni iniciar PostgreSQL.

Los reportes detallados se generan en `target/surefire-reports/`. La evidencia de la ejecución realizada acompaña esta entrega en `evidencias/`.

## Alcance de la verificación

H2 verifica las consultas JPA, transacciones y persistencia usadas en estos casos, pero no demuestra compatibilidad total con PostgreSQL. No se ha ejecutado Django ni se ha obtenido un JWT real desde su endpoint de login. Tampoco se ha probado el envío asíncrono de eventos desde Django, React, Android o WhatsApp. Es una integración interna del microservicio con contratos de Django simulados mediante datos y tokens de prueba.

Para una validación de extremo a extremo adicional se requiere un entorno PostgreSQL de pruebas con migraciones Django y ambos servicios en ejecución. El perfil H2 no debe apuntarse a una base real: usa `create-drop`.

## Evidencias de GitHub

Las evidencias deben corresponder a los commits, la rama y la pull request reales de este laboratorio en el repositorio del equipo. El resultado de pruebas incluido corresponde a la ejecución local descrita arriba.

Pasos sugeridos desde la raíz del repositorio del equipo:

```powershell
git switch -c laboratorio/pruebas-integracion
git add Spring/ferreteria-reportes-ms/pom.xml Spring/ferreteria-reportes-ms/src/test Spring/ferreteria-reportes-ms/LABORATORIO-INTEGRACION.md Spring/ferreteria-reportes-ms/Ejecutar-Laboratorio.ps1
git commit -m "Agrega laboratorio de pruebas de integracion del microservicio"
git push -u origin laboratorio/pruebas-integracion
```

Crear una pull request hacia `main` y capturar la rama, el commit, los archivos modificados y la revisión o comentarios reales del equipo. Agregar al informe la URL efectiva de esa rama o pull request. El enlace del repositorio por sí solo no acredita que estos cambios estén publicados. También conviene capturar la ejecución con `BUILD SUCCESS` y el resumen de pruebas.

## Conclusiones

1. Las pruebas de integración recorren componentes reales y comprueban tanto la respuesta HTTP como los datos persistidos; las pruebas unitarias del JWT cubren únicamente una parte del servicio.
2. Los reportes necesitan verificar estados de ventas, relaciones entre tablas y límites horarios: los datos de prueba incluyen anulaciones y ventas justo dentro y fuera del día de Lima.
3. La seguridad y la ausencia de efectos indebidos se verifican conjuntamente: una petición denegada no registra eventos y la consulta o exportación de inventario no cambia el stock.
