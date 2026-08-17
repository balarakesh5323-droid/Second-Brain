import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { GitBranch, FolderGit2 } from 'lucide-react';

export default function RepositoryExplorer() {
  const { data: repos } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories().then(r => r.data),
  });

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Repository Explorer</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {repos?.map((repo) => (
          <div key={repo.id} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <div className="flex items-center gap-3 mb-3">
              <FolderGit2 className="w-6 h-6 text-blue-400" />
              <h3 className="font-semibold">{repo.name}</h3>
            </div>
            <p className="text-sm text-gray-400 mb-2">{repo.url}</p>
            <div className="flex gap-2">
              <span className="px-2 py-1 rounded bg-gray-800 text-xs">{repo.primaryLanguage}</span>
              <span className="px-2 py-1 rounded bg-gray-800 text-xs">{repo.defaultBranch}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}