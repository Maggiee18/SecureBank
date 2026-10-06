# SecureBank web app

React 19 + TypeScript + Vite + Tailwind front end for the SecureBank API. See the main README for the
feature tour and design decisions.

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080
npm run build      # type check + production build into dist/
```

`VITE_API_BASE_URL` is only needed when the app and the API live on different domains.
