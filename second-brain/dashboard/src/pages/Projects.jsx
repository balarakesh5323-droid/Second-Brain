import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { brainApi } from '../api/client';
import {
  FolderKanban,
  Plus,
  GitBranch,
  Folder,
  RefreshCw,
  Sparkles,
  Network,
  CheckCircle2,
  AlertCircle,
  FileCode,
  CheckSquare,
  Gamepad2,
  Maximize2,
  Minimize2,
  RotateCw,
  ExternalLink,
  Download,
  Upload,
  Workflow,
  ShieldAlert,
  Code2,
} from 'lucide-react';

export default function Projects() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [showModal, setShowModal] = useState(false);
  const [showSandbox, setShowSandbox] = useState(false);
  const [showDiagramModal, setShowDiagramModal] = useState(false);
  const [activeSandboxProject, setActiveSandboxProject] = useState(null);
  const [sandboxFile, setSandboxFile] = useState('car-game.html');

  // Form states
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [path, setPath] = useState('');
  const [diagramText, setDiagramText] = useState(
    'graph TD\n  Client[Browser App] -->|HTTP REST| Gateway[API Gateway]\n  Gateway --> Auth[Auth Service]\n  Gateway --> Orders[Order Processing]\n  Orders --> DB[(PostgreSQL Database)]\n  Orders --> Events[Kafka Queue]'
  );
  const [selectedDiagramProject, setSelectedDiagramProject] = useState('');
  const [diagramSuccess, setDiagramSuccess] = useState(null);

  const [syncingId, setSyncingId] = useState(null);
  const [syncSuccess, setSyncSuccess] = useState({});

  const { data: projects, isLoading: projectsLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => brainApi.getProjects(),
  });

  const { data: repositories } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories(),
  });

  const { data: tasks } = useQuery({
    queryKey: ['tasks'],
    queryFn: () => brainApi.getOpenTasks(),
  });

  const createMutation = useMutation({
    mutationFn: (data) => brainApi.createProject(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      queryClient.invalidateQueries({ queryKey: ['graph-visual'] });
      setShowModal(false);
      setName('');
      setDescription('');
      setPath('');
    },
  });

  const diagramMutation = useMutation({
    mutationFn: (data) => brainApi.ingestDiagram(data),
    onSuccess: (res) => {
      setDiagramSuccess(res);
      queryClient.invalidateQueries({ queryKey: ['graph-visual'] });
      queryClient.invalidateQueries({ queryKey: ['graph-stats'] });
      setTimeout(() => setDiagramSuccess(null), 5000);
    },
  });

  const handleSync = async (projectId) => {
    setSyncingId(projectId);
    try {
      await brainApi.syncProject(projectId);
      setSyncSuccess((prev) => ({ ...prev, [projectId]: true }));
      queryClient.invalidateQueries({ queryKey: ['graph-visual'] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      setTimeout(() => {
        setSyncSuccess((prev) => ({ ...prev, [projectId]: false }));
      }, 4000);
    } catch (err) {
      console.error('Failed to sync project:', err);
    } finally {
      setSyncingId(null);
    }
  };

  const handleOpenSandbox = (project) => {
    setActiveSandboxProject(project);
    setSandboxFile('car-game.html');
    setShowSandbox(true);
  };

  const handleExportBackup = async () => {
    try {
      const backup = await brainApi.exportBackup();
      const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `second-brain-backup-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      alert('Failed to export backup: ' + err.message);
    }
  };

  const projectList = Array.isArray(projects) ? projects : [];
  const repoList = Array.isArray(repositories) ? repositories : [];
  const taskList = Array.isArray(tasks) ? tasks : [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white flex items-center gap-3">
            <FolderKanban className="w-7 h-7 text-indigo-400" />
            Projects & Workspaces
          </h2>
          <p className="text-gray-400 text-sm mt-1">
            Real-time file watching, AST call graph tracking, live sandbox previews, and architecture diagram ingestion.
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-wrap">
          <button
            onClick={handleExportBackup}
            className="flex items-center gap-2 bg-gray-800 hover:bg-gray-700 text-gray-200 text-xs font-semibold px-3.5 py-2.5 rounded-xl border border-gray-700 transition"
            title="Download full JSON snapshot backup of all projects, graph, and memories"
          >
            <Download className="w-4 h-4 text-emerald-400" />
            <span>Export Brain</span>
          </button>

          <button
            onClick={() => setShowDiagramModal(true)}
            className="flex items-center gap-2 bg-purple-950/60 hover:bg-purple-900/80 text-purple-200 text-xs font-semibold px-3.5 py-2.5 rounded-xl border border-purple-700/60 transition"
            title="Parse Mermaid / PlantUML diagram into Neo4j graph"
          >
            <Workflow className="w-4 h-4 text-purple-400" />
            <span>Ingest Diagram</span>
          </button>

          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold px-4 py-2.5 rounded-xl shadow-lg transition"
          >
            <Plus className="w-4 h-4" />
            <span>Create Project</span>
          </button>
        </div>
      </div>

      {/* Projects Grid */}
      {projectsLoading ? (
        <div className="text-center py-12 text-gray-500">Loading projects...</div>
      ) : projectList.length > 0 ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {projectList.map((project) => {
            const linkedRepos = repoList.filter(
              (r) => r.project?.id === project.id || r.projectId === project.id
            );
            const projectTasks = taskList.filter(
              (t) => t.project?.id === project.id || t.projectId === project.id
            );
            const isSyncing = syncingId === project.id;
            const isSynced = syncSuccess[project.id];
            const hasLocalPath = project.path && project.path.trim().length > 0;

            return (
              <div
                key={project.id}
                className="bg-gray-900 border border-gray-800 hover:border-indigo-500/50 rounded-2xl p-6 transition flex flex-col justify-between space-y-5 shadow-sm"
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-2">
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 bg-indigo-950/60 border border-indigo-800/50 rounded-xl text-indigo-400">
                        <Folder className="w-6 h-6" />
                      </div>
                      <div>
                        <h3 className="text-lg font-bold text-white flex items-center gap-2">
                          {project.name}
                        </h3>
                        <span className="text-xs text-gray-500 font-mono">ID: {project.id}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-green-950/80 text-green-400 border border-green-800/60">
                        {project.status || 'active'}
                      </span>
                    </div>
                  </div>

                  {project.description && (
                    <p className="text-sm text-gray-300 mt-2">{project.description}</p>
                  )}

                  {hasLocalPath && (
                    <div className="mt-3 flex items-center gap-2 bg-gray-950/80 px-3 py-2 rounded-lg border border-gray-800/80 text-xs font-mono text-gray-400 overflow-x-auto">
                      <FileCode className="w-4 h-4 text-indigo-400 flex-shrink-0" />
                      <span className="truncate">{project.path}</span>
                    </div>
                  )}

                  {/* Linked Repositories */}
                  <div className="mt-4 space-y-2">
                    <span className="text-xs font-semibold uppercase tracking-wider text-gray-400 flex items-center gap-1.5">
                      <GitBranch className="w-3.5 h-3.5 text-indigo-400" />
                      Linked Repositories ({linkedRepos.length})
                    </span>
                    {linkedRepos.length > 0 ? (
                      <div className="flex flex-wrap gap-2">
                        {linkedRepos.map((r) => (
                          <span
                            key={r.id}
                            className="text-xs bg-gray-800 text-gray-200 px-2.5 py-1 rounded-lg border border-gray-700 font-mono flex items-center gap-1.5"
                          >
                            <GitBranch className="w-3 h-3 text-purple-400" />
                            {r.name}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-gray-500 italic">No remote git repositories linked</p>
                    )}
                  </div>

                  {/* Open Tasks */}
                  {projectTasks.length > 0 && (
                    <div className="mt-4 space-y-1.5">
                      <span className="text-xs font-semibold uppercase tracking-wider text-gray-400 flex items-center gap-1.5">
                        <CheckSquare className="w-3.5 h-3.5 text-yellow-400" />
                        Open Tasks ({projectTasks.length})
                      </span>
                      <div className="space-y-1">
                        {projectTasks.slice(0, 3).map((t) => (
                          <div key={t.id} className="text-xs text-gray-300 flex items-center gap-2 truncate">
                            <span className="w-1.5 h-1.5 rounded-full bg-yellow-400 flex-shrink-0" />
                            <span className="truncate">{t.title}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                {/* Card Actions */}
                <div className="pt-4 border-t border-gray-800 flex items-center justify-between gap-2 flex-wrap">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleSync(project.id)}
                      disabled={isSyncing}
                      className={`flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg border transition ${
                        isSynced
                          ? 'bg-green-950/60 border-green-700 text-green-300'
                          : 'bg-gray-800 hover:bg-gray-700 border-gray-700 text-gray-200'
                      }`}
                    >
                      {isSyncing ? (
                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-indigo-400" />
                      ) : isSynced ? (
                        <CheckCircle2 className="w-3.5 h-3.5 text-green-400" />
                      ) : (
                        <RefreshCw className="w-3.5 h-3.5 text-indigo-400" />
                      )}
                      <span>{isSyncing ? 'Syncing...' : isSynced ? 'Synced' : 'Auto-Sync AST'}</span>
                    </button>

                    {hasLocalPath && (
                      <button
                        onClick={() => handleOpenSandbox(project)}
                        className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-emerald-950/50 hover:bg-emerald-900/60 border border-emerald-700/60 text-emerald-300 transition"
                        title="Run HTML/Canvas/JS games or web apps live"
                      >
                        <Gamepad2 className="w-3.5 h-3.5" />
                        <span>Play Sandbox</span>
                      </button>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => navigate('/graph')}
                      className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-purple-950/40 hover:bg-purple-900/60 border border-purple-800/60 text-purple-300 transition"
                    >
                      <Network className="w-3.5 h-3.5" />
                      <span>Graph</span>
                    </button>
                    <button
                      onClick={() => navigate('/chat')}
                      className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-indigo-950/40 hover:bg-indigo-900/60 border border-indigo-800/60 text-indigo-300 transition"
                    >
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Chat</span>
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="text-center py-16 border border-dashed border-gray-800 rounded-2xl">
          <FolderKanban className="w-12 h-12 text-gray-600 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-gray-300">No Projects Found</h3>
          <p className="text-xs text-gray-500 mt-1 max-w-sm mx-auto">
            Create a project or ask your AI agent with <code className="text-indigo-400">brain_create_project</code>.
          </p>
          <button
            onClick={() => setShowModal(true)}
            className="mt-4 inline-flex items-center gap-2 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold px-4 py-2 rounded-lg"
          >
            <Plus className="w-4 h-4" />
            <span>Create First Project</span>
          </button>
        </div>
      )}

      {/* Live Sandbox Modal (Feature 3) */}
      {showSandbox && activeSandboxProject && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 z-50">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl w-full max-w-5xl h-[85vh] flex flex-col overflow-hidden shadow-2xl">
            {/* Sandbox Header */}
            <div className="px-5 py-3.5 bg-gray-950 border-b border-gray-800 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-emerald-950 border border-emerald-700/60 rounded-lg text-emerald-400">
                  <Gamepad2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white flex items-center gap-2">
                    {activeSandboxProject.name} — Live Interactive Sandbox
                  </h3>
                  <span className="text-xs text-gray-400 font-mono">
                    Playing: {sandboxFile}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <input
                  type="text"
                  value={sandboxFile}
                  onChange={(e) => setSandboxFile(e.target.value)}
                  placeholder="filename (e.g. car-game.html)"
                  className="bg-gray-900 border border-gray-700 rounded-lg px-2.5 py-1 text-xs text-gray-200 font-mono w-48"
                />

                <a
                  href={`/api/v1/projects/${activeSandboxProject.id}/files/raw?file=${encodeURIComponent(sandboxFile)}`}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-1.5 text-xs text-gray-300 hover:text-white bg-gray-800 px-2.5 py-1.5 rounded-lg border border-gray-700"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  <span>Open in Tab</span>
                </a>

                <button
                  onClick={() => setShowSandbox(false)}
                  className="text-gray-400 hover:text-gray-200 text-sm font-bold px-2 py-1"
                >
                  ✕
                </button>
              </div>
            </div>

            {/* Sandbox Iframe */}
            <div className="flex-1 bg-gray-950 relative">
              <iframe
                title="Project Sandbox"
                src={`/api/v1/projects/${activeSandboxProject.id}/files/raw?file=${encodeURIComponent(sandboxFile)}`}
                className="w-full h-full border-0"
                sandbox="allow-scripts allow-same-origin allow-popups allow-forms"
              />
            </div>
          </div>
        </div>
      )}

      {/* Architecture Diagram Ingestion Modal (Feature 6) */}
      {showDiagramModal && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl max-w-2xl w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <Workflow className="w-5 h-5 text-purple-400" />
                Ingest Architecture Diagram to Graph
              </h3>
              <button
                onClick={() => setShowDiagramModal(false)}
                className="text-gray-400 hover:text-gray-200 text-sm"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-gray-400">
              Paste Mermaid.js or PlantUML syntax. Second Brain will automatically parse service blocks, databases, queues, and call routes into Neo4j graph nodes and relationships.
            </p>

            <div className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-gray-300 mb-1">
                  Optional Project Link
                </label>
                <select
                  value={selectedDiagramProject}
                  onChange={(e) => setSelectedDiagramProject(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3 py-2 text-xs text-gray-200"
                >
                  <option value="">Global / No Specific Project</option>
                  {projectList.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-300 mb-1">
                  Mermaid.js / Architecture Definition
                </label>
                <textarea
                  rows={8}
                  value={diagramText}
                  onChange={(e) => setDiagramText(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-xl p-3 text-xs font-mono text-gray-200 focus:outline-none focus:border-purple-500"
                />
              </div>
            </div>

            {diagramSuccess && (
              <div className="p-3 bg-green-950/50 border border-green-700/60 rounded-xl text-xs text-green-300 flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-green-400 flex-shrink-0" />
                <span>
                  Diagram ingested: {diagramSuccess.nodesCount} nodes and {diagramSuccess.relationshipsCount} relationships added to Knowledge Graph!
                </span>
              </div>
            )}

            <div className="pt-2 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowDiagramModal(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-gray-400 hover:text-gray-200"
              >
                Cancel
              </button>
              <button
                onClick={() =>
                  diagramMutation.mutate({
                    diagram: diagramText,
                    format: 'mermaid',
                    projectId: selectedDiagramProject || null,
                  })
                }
                disabled={diagramMutation.isPending}
                className="bg-purple-600 hover:bg-purple-500 text-white text-xs font-semibold px-4 py-2 rounded-xl transition"
              >
                {diagramMutation.isPending ? 'Ingesting...' : 'Ingest to Graph'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Project Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <FolderKanban className="w-5 h-5 text-indigo-400" />
                Create New Project
              </h3>
              <button
                onClick={() => setShowModal(false)}
                className="text-gray-400 hover:text-gray-200 text-sm"
              >
                ✕
              </button>
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                createMutation.mutate({ name, description, path });
              }}
              className="space-y-4"
            >
              <div>
                <label className="block text-xs font-semibold text-gray-300 mb-1">
                  Project Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. second-brain-test or CoreBanking"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2 text-sm text-gray-100 focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-300 mb-1">
                  Workspace Path (Local Directory)
                </label>
                <input
                  type="text"
                  placeholder="e.g. /home/rakesh/Documents/second-brain-test"
                  value={path}
                  onChange={(e) => setPath(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2 text-sm text-gray-100 focus:outline-none focus:border-indigo-500 font-mono text-xs"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-300 mb-1">
                  Description
                </label>
                <textarea
                  placeholder="What is this project about?"
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full bg-gray-950 border border-gray-800 rounded-xl px-3.5 py-2 text-sm text-gray-100 focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div className="pt-2 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-gray-400 hover:text-gray-200"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold px-4 py-2 rounded-xl transition"
                >
                  {createMutation.isPending ? 'Creating...' : 'Create Project'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
