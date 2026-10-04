import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import './index.css';
import App from './App.tsx';
import { queryClient } from './lib/queryClient';

// After a deploy, a tab still running the previous bundle requests lazy chunks whose
// hashed filenames no longer exist; the SPA rewrite answers with index.html and the
// import fails ("'text/html' is not a valid JavaScript MIME type"). Reload once to
// pick up the current bundle.
window.addEventListener('vite:preloadError', (event) => {
  // At most one auto-reload per 30s: if the chunk is genuinely broken, surface the
  // error instead of reload-looping.
  const last = Number(sessionStorage.getItem('chunk-reload-at') ?? 0);
  if (Date.now() - last < 30_000) return;
  sessionStorage.setItem('chunk-reload-at', String(Date.now()));
  event.preventDefault();
  window.location.reload();
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>
);
