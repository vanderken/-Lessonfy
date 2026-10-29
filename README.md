# Lessonfy frontend (auth screens)

```bash
npm install
npm run dev      # http://localhost:5173
```

- Register: http://localhost:5173/#register
- Login:    http://localhost:5173/#login
- `src/api.js`: set `MOCK = false` when Spring Boot is running on :8080
  (Vite proxies `/api` to it, so session cookies and CSRF stay same-origin).
- Mock test values: password `wrongpass` (login error), email `taken@example.com` (duplicate email).
