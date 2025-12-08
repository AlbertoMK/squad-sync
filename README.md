# SquadSync

Una aplicación web colaborativa para coordinar sesiones de juego entre amigos, optimizada para Raspberry Pi.

## 🎮 Características

- **Gestión de Usuarios**: Registro e inicio de sesión con autenticación JWT
- **Biblioteca de Juegos**: Añade juegos y califica tus preferencias (1-10)
- **Calendario de Disponibilidad**: Marca tus franjas horarias disponibles
- **Matchmaking Inteligente**: Algoritmo que encuentra las mejores sesiones basándose en:
  - Disponibilidad de jugadores
  - Preferencias de juegos
  - Rotación de juegos (evita repetir el mismo juego 3 veces seguidas)
- **Notificaciones por Email**: Recibe avisos cuando se encuentra una partida

## 🚀 Instalación

### Requisitos Previos

- Node.js 18+ 
- npm o yarn

### Configuración

1. **Clonar el repositorio**
   ```bash
   git clone <repository-url>
   cd squads-sync
   ```

2. **Instalar dependencias**
   ```bash
   npm run install:all
   ```

3. **Configurar variables de entorno**
   
   Copia el archivo `.env.example` en el backend:
   ```bash
   cd backend
   cp .env.example .env
   ```

   Edita `.env` con tus configuraciones:
   ```env
   DATABASE_URL="file:./dev.db"
   JWT_SECRET=tu-secreto-jwt-muy-seguro
   PORT=3001
   
   # SMTP para notificaciones
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USER=tu-email@gmail.com
   SMTP_PASS=tu-contraseña-de-aplicación
   SMTP_FROM=SquadSync <noreply@squadsync.com>
   
   FRONTEND_URL=http://localhost:5173
   MIN_PLAYERS_FOR_SESSION=3
   ```

4. **Inicializar la base de datos**
   ```bash
   npm run prisma:generate
   npm run prisma:migrate
   ```

## 💻 Desarrollo

### Ejecutar en modo desarrollo

**Backend** (en una terminal):
```bash
npm run dev:backend
```

**Frontend** (en otra terminal):
```bash
npm run dev:frontend
```

La aplicación estará disponible en:
- Frontend: http://localhost:5173
- Backend API: http://localhost:3001

## 📦 Producción

### Build

```bash
npm run build
```

### Ejecutar en producción

```bash
npm start
```

## 🔧 Estructura del Proyecto

```
squads-sync/
├── backend/
│   ├── prisma/
│   │   └── schema.prisma      # Esquema de base de datos
│   ├── src/
│   │   ├── config/            # Configuración
│   │   ├── middleware/        # Middleware de Express
│   │   ├── routes/            # Rutas de la API
│   │   ├── services/          # Lógica de negocio
│   │   ├── jobs/              # Cron jobs
│   │   └── index.ts           # Punto de entrada
│   └── package.json
├── frontend/
│   ├── src/
│   │   ├── components/        # Componentes React
│   │   ├── contexts/          # Contextos (Auth, etc.)
│   │   ├── lib/               # Utilidades y API client
│   │   ├── pages/             # Páginas
│   │   └── main.tsx           # Punto de entrada
│   └── package.json
└── package.json               # Scripts raíz
```

## 🎯 Uso

1. **Registrarse**: Crea una cuenta con usuario, email y contraseña
2. **Añadir Juegos**: Ve a "Juegos" y añade los juegos que juegas
3. **Calificar Preferencias**: Usa el slider para indicar cuánto te gusta cada juego (1-10)
4. **Marcar Disponibilidad**: En "Disponibilidad", selecciona las franjas horarias en las que puedes jugar
5. **Ver Sesiones**: El matchmaking se ejecuta automáticamente cada 30 minutos, o puedes ejecutarlo manualmente desde el Dashboard
6. **Recibir Notificaciones**: Recibirás un email cuando se encuentre una partida

## 🤖 Algoritmo de Matchmaking

El algoritmo funciona de la siguiente manera:

1. **Encuentra franjas horarias** donde 3+ usuarios están disponibles
2. **Calcula el score** para cada juego:
   ```
   Score = Σ(Peso de preferencia de cada usuario) + (Bonus de participación)
   Bonus de participación = Número de jugadores × 2
   ```
3. **Aplica rotación**: Si un juego ha sido elegido 3 veces seguidas, se salta
4. **Crea la sesión** con el juego de mayor score

## 📧 Configuración de Email

Para Gmail:
1. Activa la verificación en 2 pasos
2. Genera una "Contraseña de aplicación"
3. Usa esa contraseña en `SMTP_PASS`

## 🐳 Despliegue en Raspberry Pi

### Opción 1: PM2

```bash
npm install -g pm2
npm run build
pm2 start backend/dist/index.js --name squadsync
pm2 save
pm2 startup
```

### Opción 2: Docker (próximamente)

Un archivo `docker-compose.yml` será añadido para facilitar el despliegue.

## 📝 API Endpoints

### Autenticación
- `POST /api/auth/register` - Registrar usuario
- `POST /api/auth/login` - Iniciar sesión
- `GET /api/auth/me` - Obtener usuario actual

### Juegos
- `GET /api/games` - Listar juegos
- `POST /api/games` - Crear juego
- `DELETE /api/games/:id` - Eliminar juego

### Preferencias
- `GET /api/preferences` - Obtener preferencias
- `POST /api/preferences` - Establecer preferencia

### Disponibilidad
- `GET /api/availability` - Obtener disponibilidad
- `POST /api/availability` - Crear franja horaria
- `GET /api/availability/group` - Disponibilidad del grupo
- `DELETE /api/availability/:id` - Eliminar franja

### Matchmaking
- `POST /api/matchmaking/run` - Ejecutar matchmaking
- `GET /api/matchmaking/sessions` - Obtener sesiones propuestas

## 🛠️ Tecnologías

**Backend:**
- Node.js + Express
- TypeScript
- Prisma ORM
- SQLite
- JWT para autenticación
- Nodemailer para emails
- node-cron para tareas programadas

**Frontend:**
- React 19
- TypeScript
- Mantine UI
- React Router
- Axios
- react-big-calendar
- date-fns

## 📄 Licencia

ISC

## 👥 Contribuir

Las contribuciones son bienvenidas. Por favor, abre un issue primero para discutir los cambios que te gustaría hacer.
