import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import App from './App';
import { SessionProvider } from './session';
import './styles.css';

// A data router (rather than <BrowserRouter>) so the editor can block navigation while it has
// unsaved writing (useBlocker). App keeps its own <Routes> underneath this catch-all route.
const router = createBrowserRouter([
  {
    path: '*',
    element: (
      <SessionProvider>
        <App />
      </SessionProvider>
    ),
  },
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
);
