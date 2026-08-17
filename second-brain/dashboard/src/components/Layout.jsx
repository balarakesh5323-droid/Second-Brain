import { useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import {
  Brain,
  Database,
  GitBranch,
  Users,
  Zap,
  ArrowRightLeft,
  Home,
  BookOpen,
  Network,
  Sparkles,
  Trash2,
  AlertTriangle,
  Loader2,
  X,
  CheckCircle2
} from 'lucide-react';

const navItems = [
  { path: '/', label: 'Home', icon: Home },
  { path: '/chat', label: 'Brain Chat', icon: Sparkles },
  { path: '/memory', label: 'Memory', icon: Database },
  { path: '/agents', label: 'Agents', icon: Users },
  { path: '/repositories', label: 'Repositories', icon: GitBranch },
  { path: '/graph', label: 'Graph', icon: Network },
  { path: '/skills', label: 'Skills', icon: Zap },
  { path: '/handoffs', label: 'Handoffs', icon: ArrowRightLeft },
  { path: '/docs', label: 'Docs', icon: BookOpen },
];

export default function Layout() {
  const location = useLocation();
  const queryClient = useQueryClient();

  const [showWipeModal, setShowWipeModal] = useState(false);
  const [confirmInput, setConfirmInput] = useState('');
  const [isWiping, setIsWiping] = useState(false);
  const [wipeResult, setWipeResult] = useState(null);

  const handleWipe = async () => {
    if (confirmInput.toUpperCase() !== 'WIPE') return;

    setIsWiping(true);
    try {
      const res = await brainApi.wipeWholeBrain();
      setWipeResult({
        success: true,
        data: res.deleted || {},
        message: res.message || 'Entire Second Brain has been wiped successfully!',
      });
      // Invalidate and reset all queries across the dashboard
      queryClient.clear();
      queryClient.invalidateQueries();
    } catch (err) {
      setWipeResult({
        success: false,
        message: `Wipe failed: ${err.message}`,
      });
    } finally {
      setIsWiping(false);
    }
  };

  const handleCloseModal = () => {
    setShowWipeModal(false);
    setConfirmInput('');
    setWipeResult(null);
  };

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <header className="bg-gray-900 border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Brain className="w-8 h-8 text-purple-500" />
          <h1 className="text-xl font-bold">Second Brain</h1>
          <span className="text-sm text-gray-500">v1.0.0</span>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowWipeModal(true)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-semibold bg-red-950/40 hover:bg-red-900/60 border border-red-800/60 hover:border-red-600 text-red-300 transition-all cursor-pointer shadow-sm"
            title="Wipe entire Second Brain database and storage"
          >
            <Trash2 className="w-4 h-4 text-red-400" />
            <span>Wipe Brain</span>
          </button>
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
        <main className="flex-1 min-w-0 overflow-hidden p-6">
          <Outlet />
        </main>
      </div>

      {/* Wipe Confirmation Modal */}
      {showWipeModal && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl max-w-lg w-full p-6 space-y-5 shadow-2xl">
            <div className="flex items-center justify-between border-b border-gray-800 pb-3">
              <div className="flex items-center gap-2 text-red-400">
                <AlertTriangle className="w-5 h-5" />
                <h3 className="font-bold text-base text-gray-100">Wipe Entire Second Brain</h3>
              </div>
              <button
                onClick={handleCloseModal}
                disabled={isWiping}
                className="text-gray-500 hover:text-gray-300 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {!wipeResult ? (
              <div className="space-y-4">
                <div className="p-3.5 bg-red-950/30 border border-red-900/60 rounded-xl space-y-2 text-xs text-red-200 leading-relaxed">
                  <p className="font-bold text-red-300">
                    Warning: This action is permanent and cannot be undone!
                  </p>
                  <p>
                    All stored data across all engines will be completely purged:
                  </p>
                  <ul className="list-disc pl-4 space-y-1 text-[11px] text-red-300/90 font-mono">
                    <li>PostgreSQL (Memories, Projects, Repositories, Decisions, Tasks, Events)</li>
                    <li>Neo4j Knowledge Graph (AST nodes, Code relationships, Technology links)</li>
                    <li>Qdrant Vector DB (All vector collections &amp; embeddings)</li>
                    <li>Redis (Hot state &amp; caches)</li>
                  </ul>
                </div>

                <div className="space-y-2">
                  <label className="block text-xs text-gray-400 font-medium">
                    Type <span className="text-red-400 font-mono font-bold">WIPE</span> to confirm:
                  </label>
                  <input
                    type="text"
                    value={confirmInput}
                    onChange={(e) => setConfirmInput(e.target.value)}
                    placeholder="WIPE"
                    disabled={isWiping}
                    className="w-full bg-gray-950 border border-gray-700 rounded-lg px-3 py-2 text-sm font-mono text-gray-100 placeholder-gray-600 focus:outline-none focus:border-red-500"
                  />
                </div>

                <div className="flex justify-end gap-3 pt-2">
                  <button
                    onClick={handleCloseModal}
                    disabled={isWiping}
                    className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs font-medium text-gray-300 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleWipe}
                    disabled={confirmInput.toUpperCase() !== 'WIPE' || isWiping}
                    className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed text-xs font-bold text-white flex items-center gap-2 transition-all cursor-pointer shadow-lg shadow-red-950"
                  >
                    {isWiping ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span>Wiping Everything...</span>
                      </>
                    ) : (
                      <>
                        <Trash2 className="w-4 h-4" />
                        <span>Permanently Wipe Brain</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                <div
                  className={`p-4 rounded-xl border flex items-start gap-3 ${
                    wipeResult.success
                      ? 'bg-emerald-950/40 border-emerald-800 text-emerald-300'
                      : 'bg-red-950/40 border-red-800 text-red-300'
                  }`}
                >
                  {wipeResult.success ? (
                    <CheckCircle2 className="w-5 h-5 text-emerald-400 mt-0.5 flex-shrink-0" />
                  ) : (
                    <AlertTriangle className="w-5 h-5 text-red-400 mt-0.5 flex-shrink-0" />
                  )}
                  <div className="space-y-2 text-xs">
                    <p className="font-bold">{wipeResult.message}</p>
                    {wipeResult.success && wipeResult.data && (
                      <div className="grid grid-cols-2 gap-x-4 gap-y-1 font-mono text-[11px] text-gray-300 pt-1">
                        <div>Memories purged: {wipeResult.data.memories ?? 0}</div>
                        <div>Projects purged: {wipeResult.data.projects ?? 0}</div>
                        <div>Repositories purged: {wipeResult.data.repositories ?? 0}</div>
                        <div>Decisions purged: {wipeResult.data.decisions ?? 0}</div>
                        <div>Tasks purged: {wipeResult.data.tasks ?? 0}</div>
                        <div>Events purged: {wipeResult.data.events ?? 0}</div>
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex justify-end pt-2">
                  <button
                    onClick={handleCloseModal}
                    className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs font-medium text-gray-200 transition-colors cursor-pointer"
                  >
                    Close
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}