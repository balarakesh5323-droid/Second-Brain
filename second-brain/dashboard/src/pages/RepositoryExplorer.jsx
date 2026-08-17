import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { GitBranch, FolderGit2, Plus, Loader2, CheckCircle2, XCircle, ExternalLink, Code2 } from 'lucide-react';

export default function RepositoryExplorer() {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [ingestResult, setIngestResult] = useState(null);

  const { data: repos, isLoading } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories().then(r => r.data),
  });

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

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!url.trim()) return;
    addRepo.mutate(url.trim());
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">Repositories</h2>
      </div>

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
            className="flex items-center gap-2 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-700 disabled:cursor-not-allowed text-white px-5 py-2.5 rounded-lg text-sm font-medium transition-colors"
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
                    <p>Project: <span className="text-gray-300">{ingestResult.data.projectName}</span></p>
                    <p>Languages: <span className="text-gray-300">{(ingestResult.data.languages || []).join(', ') || 'N/A'}</span></p>
                    <p>Frameworks: <span className="text-gray-300">{(ingestResult.data.frameworks || []).join(', ') || 'N/A'}</span></p>
                    <p>
                      {ingestResult.data.commitsEmbedded} commits embedded, {ingestResult.data.codeFilesEmbedded} code files embedded, {ingestResult.data.graphNodesCreated} graph nodes
                    </p>
                    <p className="text-gray-500">Completed in {ingestResult.data.elapsedMs}ms</p>
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
      ) : repos?.length === 0 ? (
        <div className="text-gray-500 text-sm text-center py-12">
          No repositories indexed yet. Add a GitHub URL above to get started.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {repos?.map((repo) => (
            <div key={repo.id} className="bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors">
              <div className="flex items-center gap-3 mb-3">
                <FolderGit2 className="w-5 h-5 text-purple-400 flex-shrink-0" />
                <h3 className="font-semibold truncate">{repo.name}</h3>
              </div>
              {repo.description && (
                <p className="text-sm text-gray-400 mb-3 line-clamp-2">{repo.description}</p>
              )}
              <div className="flex items-center gap-2 text-sm text-gray-400 mb-3">
                <Code2 className="w-4 h-4" />
                <span>{repo.primaryLanguage || 'Unknown'}</span>
                <span className="text-gray-600">|</span>
                <GitBranch className="w-4 h-4" />
                <span>{repo.defaultBranch}</span>
              </div>
              <a
                href={repo.url}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-xs text-purple-400 hover:text-purple-300 transition-colors"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                Open on GitHub
              </a>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
