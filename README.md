# SquadSync

Una aplicación web colaborativa para coordinar sesiones de juego entre amigos, optimizada para Raspberry Pi.

## 🎮 Características

- **Gestión de Usuarios**: Registro e inicio de sesión con autenticación JWT.
- **Biblioteca de Juegos**: Añade juegos y califica tus preferencias (1-10).
- **Calendario de Disponibilidad**: Marca tus franjas horarias disponibles.
- **Matchmaking Inteligente**: Algoritmo que encuentra las mejores sesiones basándose en:
  - Disponibilidad de jugadores.
  - Preferencias de juegos.
  - Rotación de juegos (evita repetir el mismo juego 3 veces seguidas).
  - Duración de sesión (mínimo 1h, máximo 4h).
- **Notificaciones por Email**: Recibe avisos cuando se encuentra una partida.

## 🚀 Instalación y Despliegue (Docker)

La forma recomendada de desplegar SquadSync es utilizando **Docker** y **Docker Compose**. Esto levanta automáticamente el backend (Spring Boot), el frontend (React + Nginx), la base de datos (MySQL) y un túnel de Cloudflare para acceso remoto.

### Requisitos Previos

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)

### Pasos

1. **Clonar el repositorio**
   ```bash
   git clone https://github.com/AlbertoMK/squad-sync.git
   cd squads-sync
   ```

2. **Configurar variables de entorno**
   
   Copia el archivo de ejemplo:
   ```bash
   cp .env.example .env
   ```

   Edita el archivo `.env` con tus credenciales:
   - `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`: Credenciales de la base de datos.
   - `JWT_SECRET`: Una cadena aleatoria segura para firmar los tokens.
   - `TUNNEL_TOKEN`: El token de tu túnel de Cloudflare (si usas Cloudflare Zero Trust).
     - *Nota*: Si solo quieres probarlo rápidamente, puedes dejar el túnel temporal configurado en `docker-compose.yml` y ver la URL en los logs.

3. **Arrancar los servicios**
   ```bash
   docker-compose up -d --build
   ```

4. **Acceder a la aplicación**
   - **Localmente**: `http://localhost`
   - **Remotamente**: A través de la URL que te proporcione el túnel de Cloudflare (revisa los logs si usas un túnel temporal: `docker logs squadsync-tunnel`).

## 💻 Desarrollo Local

Si quieres contribuir o ejecutar el proyecto sin Docker:

### Backend (Spring Boot)
Requisitos: Java 17+, Maven.

```bash
cd backend_spring
mvn spring-boot:run
```
El servidor arrancará en `http://localhost:8080`.

### Frontend (React + Vite)
Requisitos: Node.js 18+.

```bash
cd frontend
npm install
npm run dev
```
La aplicación estará en `http://localhost:5173`.

## 🛠️ Tecnologías

**Backend:**
- Java 17
- Spring Boot 3
- Spring Security (JWT)
- Spring Data JPA
- MySQL
- JavaMailSender

**Frontend:**
- React 18
- TypeScript
- Vite
- Mantine UI v7
- Axios
- Tabler Icons

**Infraestructura:**
- Docker & Docker Compose
- Nginx (Reverse Proxy)
- Cloudflare Tunnel

## 📄 Licencia

ISC
