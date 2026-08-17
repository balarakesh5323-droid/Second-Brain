import { Outlet, Link, useLocation } from 'react-router-dom';
import { Brain, Database, GitBranch, Users, Zap, ArrowRightLeft, Home, BookOpen } from 'lucide-react';

const navItems = [
  { path: '/', label: 'Home', icon: Home },
  { path: '/memory', label: 'Memory', icon: Database },
  { path: '/agents', label: 'Agents', icon: Users },
  { path: '/repositories', label: 'Repositories', icon: GitBranch },
  { path: '/skills', label: 'Skills', icon: Zap },
  { path: '/handoffs', label: 'Handoffs', icon: ArrowRightLeft },
  { path: '/docs', label: 'Docs', icon: BookOpen },
];

export default function Layout() {
  const location = useLocation();

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <header className="bg-gray-900 border-b border-gray-800 px-6 py-4">
        <div className="flex items-center gap-3">
          <Brain className="w-8 h-8 text-purple-500" />
          <h1 className="text-xl font-bold">Second Brain</h1>
          <span className="text-sm text-gray-500">v1.0.0</span>
        </div>
      </header>

      <div className="flex">
        {/* Sidebar */}
        <nav className="w-64 bg-gray-900 border-r border-gray-800 min-h-[calc(100vh-73px)] p-4">
          <ul className="space-y-2">
            {navItems.map(({ path, label, icon: Icon }) => (
              <li key={path}>
                <Link
                  to={path}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                    location.pathname === path
                      ? 'bg-purple-600/20 text-purple-400'
                      : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                  <span>{label}</span>
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        {/* Main content */}
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}