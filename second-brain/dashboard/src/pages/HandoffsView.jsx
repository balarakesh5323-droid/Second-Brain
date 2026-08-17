import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { 
  ArrowRightLeft, 
  FolderGit2, 
  CheckCircle2, 
  Clock, 
  AlertTriangle, 
  FileCode, 
  ListOrdered, 
  HelpCircle,
  Bot,
  Calendar,
  Layers,
  Plus,
  X,
  Loader2
} from 'lucide-react';

export default function HandoffsView() {
  const queryClient = useQueryClient();
  const [selectedRepoId, setSelectedRepoId] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState({
    agentName: 'Antigravity AI',
    task: '',
    completedItems: '',
    inProgressItems: '',
    blockedItems: '',
    changedFiles: '',
    nextSteps: '',
    decisions: '',
    knownIssues: ''
  });

  const { data: repos, isLoading: reposLoading } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories(),
  });

  const repoList = Array.isArray(repos) ? repos : [];

  const { data: allHandoffs, isLoading: handoffsLoading } = useQuery({
    queryKey: ['handoffs'],
    queryFn: () => brainApi.getHandoffs(),
  });

  const handoffList = Array.isArray(allHandoffs) ? allHandoffs : [];

  const { data: latestHandoff, isLoading: latestLoading } = useQuery({
    queryKey: ['latestHandoff', selectedRepoId],
    queryFn: () => brainApi.getLatestHandoff(selectedRepoId),
    enabled: !!selectedRepoId,
  });

  // Selected handoff or most recent handoff from list
  const activeHandoff = selectedRepoId ? latestHandoff : (handoffList[0] || null);

  const formatList = (str) => {
    if (!str) return [];
    if (Array.isArray(str)) return str;
    return str.split('\n').map(s => s.trim()).filter(Boolean);
  };

  const handleCreateHandoff = async (e) => {
    e.preventDefault();
    if (!formData.task.trim()) return;
    setIsSubmitting(true);
    try {
      await brainApi.createHandoff({
        task: formData.task,
        completedItems: formData.completedItems,
        inProgressItems: formData.inProgressItems,
        blockedItems: formData.blockedItems,
        changedFiles: formData.changedFiles,
        nextSteps: formData.nextSteps,
        decisions: formData.decisions,
        knownIssues: formData.knownIssues,
      });
      setShowModal(false);
      setFormData({
        agentName: 'Antigravity AI',
        task: '',
        completedItems: '',
        inProgressItems: '',
        blockedItems: '',
        changedFiles: '',
        nextSteps: '',
        decisions: '',
        knownIssues: ''
      });
      queryClient.invalidateQueries({ queryKey: ['handoffs'] });
      if (selectedRepoId) {
        queryClient.invalidateQueries({ queryKey: ['latestHandoff', selectedRepoId] });
      }
    } catch (err) {
      alert('Failed to create handoff: ' + err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold">Agent Handoffs &amp; Continuity Bridge</h2>
          <p className="text-gray-400 text-xs mt-0.5">
            Cross-agent memory transfers and state snapshots between Antigravity, Cursor, Claude Code, and Codex
          </p>
        </div>

        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white text-xs font-semibold transition-all cursor-pointer shadow-lg shadow-purple-900/30"
        >
          <Plus className="w-4 h-4" />
          <span>Record Agent Handoff</span>
        </button>
      </div>

      {/* Create Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-gray-900 border border-gray-800 rounded-xl max-w-2xl w-full max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
            <div className="p-4 border-b border-gray-800 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ArrowRightLeft className="w-5 h-5 text-purple-400" />
                <h3 className="font-bold text-gray-100">Record New Agent Handoff</h3>
              </div>
              <button
                onClick={() => setShowModal(false)}
                className="text-gray-400 hover:text-gray-200 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateHandoff} className="p-5 overflow-y-auto space-y-4 text-xs">
              <div>
                <label className="block text-gray-400 font-medium mb-1">Task Summary *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Implement Graph-RAG & AST Call-Chain Extraction"
                  value={formData.task}
                  onChange={(e) => setFormData({ ...formData, task: e.target.value })}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-green-400 font-medium mb-1">Completed Items (one per line)</label>
                  <textarea
                    rows={3}
                    placeholder="Enhanced AST parsers&#10;Added Endpoint nodes in Neo4j"
                    value={formData.completedItems}
                    onChange={(e) => setFormData({ ...formData, completedItems: e.target.value })}
                    className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="block text-blue-400 font-medium mb-1">In Progress (one per line)</label>
                  <textarea
                    rows={3}
                    placeholder="Compounding memory confidence scores"
                    value={formData.inProgressItems}
                    onChange={(e) => setFormData({ ...formData, inProgressItems: e.target.value })}
                    className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-amber-400 font-medium mb-1">Blocked Items (one per line)</label>
                  <textarea
                    rows={2}
                    placeholder="None"
                    value={formData.blockedItems}
                    onChange={(e) => setFormData({ ...formData, blockedItems: e.target.value })}
                    className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                  />
                </div>
                <div>
                  <label className="block text-purple-400 font-medium mb-1">Next Steps (one per line)</label>
                  <textarea
                    rows={2}
                    placeholder="Rebuild and test Docker cluster"
                    value={formData.nextSteps}
                    onChange={(e) => setFormData({ ...formData, nextSteps: e.target.value })}
                    className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-cyan-400 font-medium mb-1">Changed Files (one per line)</label>
                <textarea
                  rows={2}
                  placeholder="second-brain/backend/src/main/java/.../RepositoryIngestionService.java"
                  value={formData.changedFiles}
                  onChange={(e) => setFormData({ ...formData, changedFiles: e.target.value })}
                  className="w-full bg-gray-950 border border-gray-800 rounded-lg p-2.5 text-gray-100 placeholder-gray-600 focus:outline-none focus:border-purple-500 font-mono"
                />
              </div>

              <div className="pt-3 border-t border-gray-800 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 rounded-lg bg-purple-600 hover:bg-purple-700 text-white font-semibold flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-50"
                >
                  {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                  <span>Save Handoff Snapshot</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Repository / Handoff Selector */}
        <div className="space-y-4">
          <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider">
            Repositories ({repoList.length})
          </h3>

          {reposLoading ? (
            <div className="text-gray-500 text-sm">Loading repositories...</div>
          ) : repoList.length === 0 ? (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 text-sm text-gray-500">
              No repositories found. Add a repository to see handoff continuity.
            </div>
          ) : (
            <div className="space-y-2">
              {repoList.map((repo) => {
                const isSelected = selectedRepoId === repo.id;
                return (
                  <button
                    key={repo.id}
                    onClick={() => setSelectedRepoId(repo.id)}
                    className={`w-full text-left p-4 rounded-xl border transition-all cursor-pointer ${
                      isSelected
                        ? 'bg-purple-950/40 border-purple-500 text-purple-200'
                        : 'bg-gray-900 border-gray-800 hover:border-gray-700 text-gray-300'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <FolderGit2 className={`w-5 h-5 ${isSelected ? 'text-purple-400' : 'text-gray-500'}`} />
                      <div className="min-w-0 flex-1">
                        <p className="font-medium truncate">{repo.name}</p>
                        <p className="text-xs text-gray-500 truncate">{repo.primaryLanguage || 'Repository'}</p>
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {handoffList.length > 0 && (
            <div className="pt-4 border-t border-gray-800">
              <h3 className="text-sm font-semibold text-gray-400 uppercase tracking-wider mb-2">
                Recent Handoff Feed ({handoffList.length})
              </h3>
              <div className="space-y-2">
                {handoffList.slice(0, 5).map((h) => (
                  <div key={h.id} className="bg-gray-900/60 border border-gray-800/80 rounded-lg p-3 text-xs">
                    <div className="flex items-center justify-between text-gray-400 mb-1">
                      <span className="font-medium text-gray-300">{h.agent?.name || 'Agent'}</span>
                      <span>{h.createdAt ? new Date(h.createdAt).toLocaleDateString() : ''}</span>
                    </div>
                    <p className="text-gray-400 line-clamp-1">{h.task || 'Handoff'}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Detailed Handoff Inspector */}
        <div className="lg:col-span-2">
          {latestLoading ? (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-8 text-center text-gray-500">
              Loading handoff details...
            </div>
          ) : activeHandoff ? (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-6">
              {/* Header */}
              <div className="flex flex-wrap items-start justify-between gap-4 pb-4 border-b border-gray-800">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="px-2.5 py-1 rounded bg-purple-600/20 text-purple-400 text-xs font-medium">
                      Active Handoff
                    </span>
                    <span className="text-xs text-gray-500 font-mono">
                      ID: {activeHandoff.id?.slice(0, 8)}...
                    </span>
                  </div>
                  <h3 className="text-xl font-bold mt-2 text-gray-100">
                    {activeHandoff.task || 'Task in Progress'}
                  </h3>
                </div>

                <div className="flex items-center gap-4 text-xs text-gray-400">
                  <div className="flex items-center gap-1.5 bg-gray-800 px-3 py-1.5 rounded-lg">
                    <Bot className="w-4 h-4 text-purple-400" />
                    <span>{activeHandoff.agent?.name || 'Previous Agent'}</span>
                  </div>
                  {activeHandoff.createdAt && (
                    <div className="flex items-center gap-1.5 bg-gray-800 px-3 py-1.5 rounded-lg">
                      <Calendar className="w-4 h-4 text-gray-400" />
                      <span>{new Date(activeHandoff.createdAt).toLocaleString()}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Sections */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Completed Items */}
                <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                  <div className="flex items-center gap-2 text-green-400 text-sm font-semibold mb-2">
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Completed Items</span>
                  </div>
                  {activeHandoff.completedItems ? (
                    <ul className="space-y-1.5 text-sm text-gray-300">
                      {formatList(activeHandoff.completedItems).map((item, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="text-green-500">•</span>
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-gray-500 italic">None reported</p>
                  )}
                </div>

                {/* In Progress */}
                <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                  <div className="flex items-center gap-2 text-blue-400 text-sm font-semibold mb-2">
                    <Clock className="w-4 h-4" />
                    <span>In Progress</span>
                  </div>
                  {activeHandoff.inProgressItems ? (
                    <ul className="space-y-1.5 text-sm text-gray-300">
                      {formatList(activeHandoff.inProgressItems).map((item, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="text-blue-500">•</span>
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-gray-500 italic">None reported</p>
                  )}
                </div>

                {/* Blocked Items */}
                <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                  <div className="flex items-center gap-2 text-amber-400 text-sm font-semibold mb-2">
                    <AlertTriangle className="w-4 h-4" />
                    <span>Blocked Items</span>
                  </div>
                  {activeHandoff.blockedItems ? (
                    <ul className="space-y-1.5 text-sm text-gray-300">
                      {formatList(activeHandoff.blockedItems).map((item, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="text-amber-500">•</span>
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-gray-500 italic">None</p>
                  )}
                </div>

                {/* Next Steps */}
                <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                  <div className="flex items-center gap-2 text-purple-400 text-sm font-semibold mb-2">
                    <ListOrdered className="w-4 h-4" />
                    <span>Next Steps</span>
                  </div>
                  {activeHandoff.nextSteps ? (
                    <ul className="space-y-1.5 text-sm text-gray-300">
                      {formatList(activeHandoff.nextSteps).map((item, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="text-purple-500">•</span>
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-gray-500 italic">None</p>
                  )}
                </div>
              </div>

              {/* Changed Files */}
              {activeHandoff.changedFiles && (
                <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                  <div className="flex items-center gap-2 text-gray-300 text-sm font-semibold mb-2">
                    <FileCode className="w-4 h-4 text-cyan-400" />
                    <span>Changed Files</span>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {formatList(activeHandoff.changedFiles).map((file, i) => (
                      <span key={i} className="px-2.5 py-1 bg-gray-900 border border-gray-800 rounded font-mono text-xs text-gray-300">
                        {file}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Decisions & Known Issues */}
              {(activeHandoff.decisions || activeHandoff.knownIssues) && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {activeHandoff.decisions && (
                    <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                      <div className="flex items-center gap-2 text-cyan-400 text-sm font-semibold mb-2">
                        <Layers className="w-4 h-4" />
                        <span>Decisions Made</span>
                      </div>
                      <p className="text-sm text-gray-300 whitespace-pre-wrap">{activeHandoff.decisions}</p>
                    </div>
                  )}

                  {activeHandoff.knownIssues && (
                    <div className="bg-gray-950/60 border border-gray-800/80 rounded-lg p-4">
                      <div className="flex items-center gap-2 text-red-400 text-sm font-semibold mb-2">
                        <HelpCircle className="w-4 h-4" />
                        <span>Known Issues</span>
                      </div>
                      <p className="text-sm text-gray-300 whitespace-pre-wrap">{activeHandoff.knownIssues}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-12 text-center">
              <ArrowRightLeft className="w-12 h-12 text-gray-600 mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-gray-300">No Handoff Recorded Yet</h3>
              <p className="text-sm text-gray-500 max-w-md mx-auto mt-2">
                When coding agents (Claude Code, Codex, Cursor) conclude a session, they leave a structured handoff in the Second Brain using <code className="text-purple-400">brain_create_handoff</code>.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}