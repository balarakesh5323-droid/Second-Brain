import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import {
  GitBranch,
  FolderGit2,
  Plus,
  Loader2,
  CheckCircle2,
  XCircle,
  ExternalLink,
  Code2,
  RefreshCw,
  Terminal,
  Copy,
  Check,
  X
} from 'lucide-react';

export default function RepositoryExplorer() {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [ingestResult, setIngestResult] = useState(null);
  const [syncingRepoId, setSyncingRepoId] = useState(null);
  const [syncMessage, setSyncMessage] = useState(null);
  const [showHookModal, setShowHookModal] = useState(false);
  const [copiedHook, setCopiedHook] = useState(false);

  const { data: repos, isLoading } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories(),
  });

  const { data: hookScriptData } = useQuery({
    queryKey: ['gitHookScript'],
    queryFn: () => brainApi.getGitHookScript(window.location.origin),
    enabled: showHookModal,
  });

  const repoList = Array.isArray(repos) ? repos : [];

  const addRepo = useMutation({
    mutationFn: (repoUrl) => brainApi.addRepository(repoUrl),
    onMutate: () => setIngestResult(null),
    onSuccess: (res) => {
      setIngestResult({ success: true, data: res.data });
      setUrl('');
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
    },
    onError: (err) => {
      setIngestResult({
        success: false,
        error: err.response?.data?.error || err.message,
      });
    },
  });

  const handleSyncRepo = async (repoId, repoName) => {
    setSyncingRepoId(repoId);
    setSyncMessage(null);
    try {
      await brainApi.syncRepository(repoId);
      setSyncMessage({
        success: true,
        text: `Successfully synced ${repoName} with latest Git commits & updated Graph AST!`,
      });
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
      queryClient.invalidateQueries({ queryKey: ['graphVisual'] });
    } catch (err) {
      setSyncMessage({
        success: false,
        text: `Sync failed: ${err.message}`,
      });
    } finally {
      setSyncingRepoId(null);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!url.trim()) return;
    addRepo.mutate(url.trim());
  };

  const handleCopyHook = () => {
    const script = hookScriptData?.script || `#!/bin/sh\n# Second Brain Auto-Ingestion Post-Commit Hook\nREPO_URL=$(git config --get remote.origin.url 2>/dev/null || echo "")\nif [ -n "$REPO_URL" ]; then\n  curl -s -X POST "http://localhost:8080/api/v1/repository-intel/add-url" -H "Content-Type: application/json" -d "{\\"url\\": \\"$REPO_URL\\"}" > /dev/null 2>&1 &\nfi\n`;
    navigator.clipboard.writeText(script);
    setCopiedHook(true);
    setTimeout(() => setCopiedHook(false), 2000);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">Repository Explorer</h2>
          <p className="text-xs text-gray-400 mt-0.5">
            Manage tracked codebases, trigger real-time Git syncs, or configure auto-ingestion hooks
          </p>
        </div>
        <button
          onClick={() => setShowHookModal(true)}
          className="flex items-center gap-2 bg-gray-900 hover:bg-gray-800 border border-gray-700 hover:border-purple-500 text-gray-200 px-4 py-2 rounded-lg text-xs font-medium transition-colors cursor-pointer"
        >
          <Terminal className="w-4 h-4 text-purple-400" />
          <span>Setup Git Post-Commit Hook</span>
        </button>
      </div>

      {/* Sync Status Toast */}
      {syncMessage && (
        <div
          className={`p-3 rounded-lg border text-xs flex items-center justify-between ${
            syncMessage.success ? 'bg-emerald-950/40 border-emerald-800 text-emerald-300' : 'bg-red-950/40 border-red-800 text-red-300'
          }`}
        >
          <span>{syncMessage.text}</span>
          <button onClick={() => setSyncMessage(null)} className="text-gray-400 hover:text-gray-200 cursor-pointer">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Add Repository Form */}
      <form onSubmit={handleSubmit} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
        <label className="block text-sm font-medium text-gray-400 mb-2">
          Add GitHub Repository
        </label>
        <div className="flex gap-3">
          <input
            type="text"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://github.com/owner/repo"
            className="flex-1 bg-gray-800 border border-gray-700 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:border-purple-500 transition-colors"
            disabled={addRepo.isPending}
          />
          <button
            type="submit"
            disabled={addRepo.isPending || !url.trim()}
            className="flex items-center gap-2 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-700 disabled:cursor-not-allowed text-white px-5 py-2.5 rounded-lg text-sm font-medium transition-colors cursor-pointer"
          >
            {addRepo.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Ingesting...
              </>
            ) : (
              <>
                <Plus className="w-4 h-4" />
                Add Repository
              </>
            )}
          </button>
        </div>
      </form>

      {/* Ingestion Result */}
      {ingestResult && (
        <div className={`rounded-xl p-5 border ${ingestResult.success ? 'bg-green-900/20 border-green-800' : 'bg-red-900/20 border-red-800'}`}>
          <div className="flex items-start gap-3">
            {ingestResult.success ? (
              <CheckCircle2 className="w-5 h-5 text-green-400 mt-0.5 flex-shrink-0" />
            ) : (
              <XCircle className="w-5 h-5 text-red-400 mt-0.5 flex-shrink-0" />
            )}
            <div className="flex-1 min-w-0">
              {ingestResult.success ? (
                <div className="space-y-1">
                  <p className="font-medium text-green-300">Repository ingested successfully</p>
                  <div className="text-sm text-gray-400 space-y-0.5">
                    <p>Project: <span className="text-gray-300">{ingestResult.data?.projectName}</span></p>
                    <p>Languages: <span className="text-gray-300">{(ingestResult.data?.languages || []).join(', ') || 'N/A'}</span></p>
                    <p>Frameworks: <span className="text-gray-300">{(ingestResult.data?.frameworks || []).join(', ') || 'N/A'}</span></p>
                    <p>
                      {ingestResult.data?.commitsEmbedded || 0} commits embedded, {ingestResult.data?.codeFilesEmbedded || 0} code files embedded, {ingestResult.data?.graphNodesCreated || 0} graph nodes
                    </p>
                    <p className="text-gray-500">Completed in {ingestResult.data?.elapsedMs || 0}ms</p>
                  </div>
                </div>
              ) : (
                <p className="text-red-300 text-sm">{ingestResult.error}</p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Repository List */}
      {isLoading ? (
        <div className="text-gray-500 text-sm">Loading repositories...</div>
      ) : repoList.length === 0 ? (
        <div className="text-gray-500 text-sm text-center py-12">
          No repositories indexed yet. Add a GitHub URL above to get started.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {repoList.map((repo) => (
            <div key={repo.id} className="bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <FolderGit2 className="w-5 h-5 text-purple-400 flex-shrink-0" />
                    <h3 className="font-semibold truncate">{repo.name}</h3>
                  </div>
                  <button
                    onClick={() => handleSyncRepo(repo.id, repo.name)}
                    disabled={syncingRepoId === repo.id}
                    title="Pull latest Git commits & re-index AST"
                    className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-gray-800 hover:bg-purple-600/30 text-gray-300 hover:text-purple-300 text-xs border border-gray-700 transition-all cursor-pointer disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${syncingRepoId === repo.id ? 'animate-spin text-purple-400' : ''}`} />
                    <span>{syncingRepoId === repo.id ? 'Syncing...' : 'Sync Git'}</span>
                  </button>
                </div>
                {repo.description && (
                  <p className="text-sm text-gray-400 mb-3 line-clamp-2">{repo.description}</p>
                )}
                <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
                  <Code2 className="w-4 h-4" />
                  <span>{repo.primaryLanguage || 'Unknown'}</span>
                  <span className="text-gray-600">|</span>
                  <GitBranch className="w-4 h-4" />
                  <span>{repo.defaultBranch || 'main'}</span>
                </div>
              </div>
              {repo.url && (
                <div className="pt-3 border-t border-gray-800/80 flex items-center justify-between">
                  <a
                    href={repo.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 text-xs text-purple-400 hover:text-purple-300 transition-colors"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    Open on GitHub
                  </a>
                  <span className="text-[10px] text-gray-500 font-mono">
                    ID: {repo.id?.substring(0, 8)}...
                  </span>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Git Hook Setup Modal */}
      {showHookModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-gray-900 border border-gray-800 rounded-2xl max-w-xl w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-gray-800 pb-3">
              <div className="flex items-center gap-2">
                <Terminal className="w-5 h-5 text-purple-400" />
                <h3 className="font-bold text-base text-gray-100">Git Post-Commit Auto-Ingestion Hook</h3>
              </div>
              <button onClick={() => setShowHookModal(false)} className="text-gray-500 hover:text-gray-300 cursor-pointer">
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-gray-300 leading-relaxed">
              Install this hook in any local git repository to automatically trigger Second Brain real-time ingestion whenever you make a commit or push!
            </p>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs text-gray-400 font-mono">
                <span>.git/hooks/post-commit</span>
                <button
                  onClick={handleCopyHook}
                  className="flex items-center gap-1 text-purple-400 hover:text-purple-300 cursor-pointer"
                >
                  {copiedHook ? <Check className="w-3.5 h-3.5 text-green-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedHook ? 'Copied script' : 'Copy script'}</span>
                </button>
              </div>
              <pre className="bg-gray-950 border border-gray-800 rounded-lg p-3 text-xs font-mono text-emerald-400 overflow-x-auto">
                {hookScriptData?.script || `#!/bin/sh\n# Second Brain Auto-Ingestion Post-Commit Hook\nREPO_URL=$(git config --get remote.origin.url 2>/dev/null || echo "")\nif [ -n "$REPO_URL" ]; then\n  curl -s -X POST "http://localhost:8080/api/v1/repository-intel/add-url" \\\n    -H "Content-Type: application/json" \\\n    -d "{\\"url\\": \\"$REPO_URL\\"}" > /dev/null 2>&1 &\nfi\n`}
              </pre>
            </div>

            <div className="bg-gray-950 p-3 rounded-lg border border-gray-800/80 text-xs space-y-1 text-gray-400">
              <span className="font-semibold text-purple-300 block">Quick Install Command:</span>
              <code className="text-gray-300 font-mono block bg-gray-900 p-2 rounded break-all select-all">
                curl -s http://localhost:8080/api/v1/repository-intel/git-hook-script | jq -r .script &gt; .git/hooks/post-commit &amp;&amp; chmod +x .git/hooks/post-commit
              </code>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => setShowHookModal(false)}
                className="px-4 py-2 rounded-lg bg-gray-800 hover:bg-gray-700 text-xs font-medium text-gray-200 transition-colors cursor-pointer"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

