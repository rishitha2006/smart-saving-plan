# Smart Saving Plan — React Frontend

This version keeps the original Smart Saving Plan visual design and CSS while using React as the frontend shell.

## Important
- The original `src/main/resources/static/css/app.css` is preserved unchanged.
- The same Thymeleaf templates are still used by the Spring backend to calculate and render the original page markup.
- React mounts that original markup and handles client navigation, forms, OCR, voice entry, theme, notifications, filtering, and downloads.
- The backend/database logic was not rewritten or replaced.

## Run

### 1. Start MongoDB
Make sure MongoDB is running using the connection configured in `src/main/resources/application.properties`.

### 2. Start Spring Boot
From the project root:

```bash
mvn spring-boot:run
```

Spring Boot runs on `http://localhost:8080`.

### 3. Start React
In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal, normally `http://localhost:5173`.

The Vite proxy forwards the existing Spring endpoints to port 8080.

## Build React

```bash
cd frontend
npm install
npm run build
```

The production files are generated in `frontend/dist`.

## Design preservation

Do not replace `frontend/public/css/app.css` with a new stylesheet if you want the exact original appearance. It is the same byte-for-byte stylesheet as the original project.
