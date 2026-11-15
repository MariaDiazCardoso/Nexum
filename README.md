# NEXUM
NEXUM utiliza Angular para la interfaz, Java con Spring Boot para el servidor y PostgreSQL para guardar los datos. Primero restaura la base de datos y configura el servidor; después inicia ambos servicios.

## 1 Requisitos

| Programa | Versión o condición |
|---|---|
| Java JDK | 21 |
| Node.js con npm | Compatible con Angular 21; por ejemplo Node 22.12 o posterior de la rama 22 |
| PostgreSQL | 17 para reproducir el entorno del proyecto |
| pgAdmin | Para crear y restaurar la base de datos |
| VS Code | Para abrir el proyecto, editar su configuración y ejecutar ambos servicios |
| Terminal integrada de VS Code | Selecciona el perfil PowerShell para los comandos de esta guía |


En VS Code selecciona **Archivo → Abrir carpeta** y abre la carpeta principal completa del proyecto. Luego selecciona **Terminal → Nueva terminal**. En el selector de perfiles de la terminal elige **PowerShell** y comprueba las instalaciones:

```powershell
java -version
node --version
npm --version
```

## 2 Preparar la carpeta

En el explorador lateral de VS Code debes ver esta estructura. Si recibiste un ZIP, extráelo antes de abrir la carpeta:

```text
NEXUM_SEGUNDO AVANCE/
  NEXUM-backend/
  NEXUM-frontend/
  base-datos/
  iniciar-nexum.ps1
  README.md
```

La carpeta superior puede tener otro nombre. Mantén `iniciar-nexum.ps1` en el mismo nivel que `NEXUM-backend` y `NEXUM-frontend`; el arranque depende de esta organización.

## 3 Restaurar la base de datos

Crea una base vacía llamada `nexum_herramientas_local` desde pgAdmin. Desde PowerShell, en la carpeta principal del proyecto, ejecuta:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -h 127.0.0.1 -U postgres -d nexum_herramientas_local -v ON_ERROR_STOP=1 -f '.\base-datos\nexum_herramientas_local.sql'
```

Ajusta la ruta a `psql.exe` y el usuario de PostgreSQL si tu instalación usa otros valores. El comando te pedirá la contraseña de PostgreSQL.

El respaldo conserva los cursos y sus relaciones. Los correos del respaldo son ficticios y las credenciales anteriores están deshabilitadas. Registra una cuenta nueva desde la web con un correo que controles. Para la exposición, cambia `tu-correo@example.invalid` por el correo de esa cuenta en `base-datos/DEMOSTRACION-BD.sql`.

En pgAdmin, desde Query Tool de la base restaurada, comprueba los cursos:

```sql
SELECT titulo, estado, precio FROM public.curso;
```

El backend usa `ddl-auto=validate` y necesita la BD restaurada antes de iniciar.

## 4 Configurar el backend

Desde el explorador lateral de VS Code, haz clic derecho en `NEXUM-backend` y selecciona **Nueva carpeta** para crear `.local`, si no existe. Dentro usa **Nuevo archivo** y escribe exactamente:

```text
application-local.properties
```

La ruta final debe ser `NEXUM-backend/.local/application-local.properties`, sin una extensión `.txt` adicional.

**Si ya tienes ese archivo con una configuración que funciona, consérvalo: no lo sobrescribas con el ejemplo.** En una instalación nueva, usa esta estructura y reemplaza los marcadores entre ángulos:

```properties
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/nexum_herramientas_local
spring.datasource.username=postgres
spring.datasource.password=<CONTRASENA_DE_TU_POSTGRESQL>
jwt.secret=<CLAVE_GENERADA_EN_BASE64>
```

La contraseña corresponde al usuario PostgreSQL indicado, no a una cuenta de NEXUM. Si PostgreSQL utiliza otro puerto, usuario o nombre de base, ajusta la configuración. En archivos `.properties`, una barra invertida literal debe escribirse como `\\`.

### Generar la clave JWT

En la terminal integrada de VS Code, con el perfil PowerShell, ejecuta lo siguiente y copia el resultado completo después de `jwt.secret=` en el archivo local. Guarda el archivo con **Ctrl+S**:

```powershell
$bytes = New-Object byte[] 32
$generador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$generador.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$generador.Dispose()
```

Genera la clave una sola vez para esa instalación y consérvala. No uses una palabra corta ni cambies la clave en cada arranque: cambiarla invalida las sesiones existentes.

**No subas el archivo local real a GitHub ni lo incluyas en un ZIP para otras personas.** Cada instalación debe usar sus propias credenciales. El archivo general `src/main/resources/application.properties` importa esta configuración local.

## 5 Configurar Gmail para los correos

Para verificar cuentas nuevas y recuperar contraseñas se necesita una cuenta Gmail con una contraseña de aplicación de Google.

Después de crear la configuración básica del paso anterior, en VS Code haz clic derecho sobre la **carpeta principal** y selecciona **Abrir en terminal integrada**. Ejecuta allí:

```powershell
powershell -ExecutionPolicy Bypass -File .\iniciar-nexum.ps1 -Servicio Correo
```

Introduce tu Gmail y la contraseña de aplicación de 16 letras cuando el script lo solicite. No uses la contraseña normal de Google. El script guarda la configuración SMTP en el archivo local:

| Parámetro | Valor o procedencia |
|---|---|
| Servidor SMTP | `smtp.gmail.com` |
| Puerto | `587` |
| Usuario y remitente | Tu Gmail |
| Contraseña | Contraseña de aplicación de Google |
| Conexión | Autenticación y STARTTLS |

Reinicia el backend si cambias esta configuración. Sin SMTP configurado puedes consultar el catálogo, pero no demostrar el envío de verificación o recuperación; el contenido para alumnos requiere una cuenta verificada y una inscripción válida.

## 6 Iniciar los servicios

PostgreSQL debe permanecer iniciado. No es necesario dejar abierta la ventana de pgAdmin.

### Abrir dos terminales dentro de VS Code

En VS Code selecciona **Terminal → Nueva terminal**. Comprueba que la ruta mostrada corresponde a la carpeta principal del proyecto.

**Primera terminal para el backend:**

```powershell
cd .\NEXUM-backend
npm start
```

Este comando ejecuta el script de arranque y Maven. El backend no necesita `npm install`. En la primera ejecución Maven descargará sus dependencias; espera a que el registro confirme que Spring Boot arrancó en el puerto 8086.

**Segunda terminal para el frontend:** pulsa el botón **+** del panel de terminales de VS Code para abrir otra terminal, sin cerrar la del backend. Debe comenzar en la carpeta principal; si comienza dentro de `NEXUM-backend`, ejecuta primero `cd ..`.

```powershell
cd .\NEXUM-frontend
npm ci
npm start
```

Ejecuta `npm ci` la primera vez o cuando necesites reinstalar las dependencias del archivo `package-lock.json`. Para los siguientes arranques basta `npm start`.

Si la terminal integrada muestra un error sobre `npm.ps1` o ejecución de scripts, utiliza `npm.cmd start` en lugar de `npm start`, y `npm.cmd ci` en lugar de `npm ci`. Tienen el mismo propósito y se ejecutan dentro de VS Code.

Mantén ambas terminales abiertas mientras utilizas NEXUM. Para detenerlo, selecciona cada terminal y pulsa **Ctrl+C**. Para volver a iniciar, ejecuta `npm start` en cada carpeta. No necesitas ejecutar `clean` para arrancar.

## 7 Abrir y comprobar NEXUM

Abre **http://127.0.0.1:4306** en el navegador.

| Servicio | Dirección o puerto |
|---|---|
| Frontend | `http://127.0.0.1:4306` |
| Backend | `http://127.0.0.1:8086` |
| PostgreSQL | `127.0.0.1:5432` |

Comprueba:

1. El catálogo muestra los cursos de la BD restaurada.
2. Puedes registrar y verificar una cuenta usando un correo que controles.
3. Puedes iniciar sesión y acceder al perfil.
4. Puedes inscribirte en un curso gratuito y abrir sus lecciones.
5. Las evaluaciones publicadas muestran preguntas y guardan resultados.
6. El progreso permanece después de recargar la página.

Las contraseñas de las cuentas restauradas no se pueden leer desde la BD porque se guardan como hash. Para una cuenta de demostración existente, solicita las credenciales al responsable por un medio acordado; no publiques contraseñas en este archivo. Registrar una cuenta desde la interfaz crea un alumno, no un administrador.

Comprobación adicional del servidor: abre una tercera terminal integrada de VS Code y ejecuta:

```powershell
Invoke-RestMethod http://127.0.0.1:8086/api/salud
```

Debe responder `estado: ok`. Esta ruta confirma que el servidor responde; comprueba también el catálogo para verificar el recorrido con la BD.

## 8 Alcance de esta entrega

El proyecto incluye registro, correo, catálogo, inscripción gratuita, aprendizaje, evaluaciones, progreso y certificados. La integración de cobros reales y la IA quedan pendientes: las pantallas de pago no realizan cobros.