import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// Fon dibundel offline dari paket npm (bukan CDN)
import '@fontsource-variable/fraunces';
import '@fontsource-variable/fraunces/wght-italic.css';
import '@fontsource-variable/plus-jakarta-sans';
import './styles/tokens.css';
import './styles/app.css';

import { App } from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
