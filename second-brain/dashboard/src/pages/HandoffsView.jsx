import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { ArrowRightLeft, User, FileText } from 'lucide-react';

export default function HandoffsView() {
  const { data: repos } = useQuery({
    queryKey: ['repositories'],
    queryFn: () => brainApi.getRepositories().then(r => r.data),
  });

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Agent Handoffs</h2>
      <p className="text-gray-400">Select a repository to view its latest handoff.</p>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {repos?.map((repo) => (
          <div key={repo.id} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <div className="flex items-center gap-3">
              <FileText className="w-6 h-6 text-green-400" />
              <h3 className="font-semibold">{repo.name}</h3>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}