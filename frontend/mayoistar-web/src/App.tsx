import React, { useState, useEffect } from 'react';
import { getAccessToken, setAccessToken, registerToastHandler } from './api/client';
import { logout } from './api/adminAuth';
import { SidebarNav } from './components/SidebarNav';
import { TopBar } from './components/TopBar';
import { ToastContainer, ToastItem } from './components/Toast';

// Pages
import { Login } from './pages/Login';
import { ActivationLanding } from './pages/ActivationLanding';
import { Workbench } from './pages/Workbench';
import { Activities } from './pages/Activities';
import { Users } from './pages/Users';
import { Merchants } from './pages/Merchants';
import { Teams } from './pages/Teams';
import { Reports } from './pages/Reports';
import { SettingsPassword } from './pages/SettingsPassword';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(!!getAccessToken());
  const [currentRoute, setCurrentRoute] = useState<string>('workbench');
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);

  // Setup global toast notifications
  useEffect(() => {
    registerToastHandler((message, type) => {
      const id = Date.now().toString() + Math.random().toString().slice(2, 6);
      setToasts((prev) => [...prev, { id, message, type }]);

      // Auto-dismiss after 4 seconds
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
      }, 4000);
    });
  }, []);

  const handleDismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch (e) {
      console.error('Logout request failed, fallback to state clearing:', e);
    } finally {
      setAccessToken('');
      setIsAuthenticated(false);
      setCurrentRoute('workbench');
    }
  };

  const renderPage = () => {
    switch (currentRoute) {
      case 'workbench':
        return <Workbench onNavigate={setCurrentRoute} />;
      case 'activities':
        return <Activities />;
      case 'users':
        return <Users />;
      case 'merchants':
        return <Merchants />;
      case 'teams':
        return <Teams />;
      case 'reports':
        return <Reports />;
      case 'settings/password':
        return <SettingsPassword />;
      default:
        return <Workbench onNavigate={setCurrentRoute} />;
    }
  };

  // 激活落地页：绕过管理员认证门禁，允许未登录用户直接访问
  if (window.location.pathname === '/activate') {
    return <ActivationLanding />;
  }

  if (!isAuthenticated) {
    return (
      <div className="relative min-h-screen bg-slate-50 text-slate-800">
        <Login onLoginSuccess={() => setIsAuthenticated(true)} />
        <ToastContainer toasts={toasts} onDismiss={handleDismissToast} />
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-slate-50 font-sans antialiased text-slate-800">
      <SidebarNav
        currentRoute={currentRoute}
        onNavigate={setCurrentRoute}
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed((prev) => !prev)}
      />
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <TopBar onLogout={handleLogout} />
        <main className="flex-1 overflow-y-auto p-8 max-w-7xl w-full mx-auto">{renderPage()}</main>
      </div>
      <ToastContainer toasts={toasts} onDismiss={handleDismissToast} />
    </div>
  );
}
