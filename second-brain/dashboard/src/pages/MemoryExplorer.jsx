import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Search, Filter } from 'lucide-react';

export default function MemoryExplorer() {
  const [query, setQuery] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  const { data: searchResults } = useQuery({
    queryKey: ['memorySearch', searchTerm],
    queryFn: () => brainApi.searchMemory(searchTerm).then(r => r.data),
    enabled: !!searchTerm,
  });

  const { data: allMemories } = useQuery({
    queryKey: ['memories'],
    queryFn: () => brainApi.getMemories().then(r => r.data),
    enabled: !searchTerm,
  });

  const memories = searchTerm ? searchResults : allMemories;

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Memory Explorer</h2>
      
      {/* Search bar */}
      <div className="flex gap-3">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-3 w-5 h-5 text-gray-500" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && setSearchTerm(query)}
            placeholder="Search memories..."
            className="w-full pl-10 pr-4 py-3 bg-gray-900 border border-gray-700 rounded-lg text-gray-100 placeholder-gray-500 focus:outline-none focus:border-purple-500"
          />
        </div>
        <button
          onClick={() => setSearchTerm(query)}
          className="px-6 py-3 bg-purple-600 hover:bg-purple-700 rounded-lg font-medium transition-colors"
        >
          Search
        </button>
        {searchTerm && (
          <button
            onClick={() => { setQuery(''); setSearchTerm(''); }}
            className="px-4 py-3 bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors"
          >
            Clear
          </button>
        )}
      </div>

      {/* Memory list */}
      <div className="space-y-3">
        {memories?.map((memory) => (
          <div key={memory.id} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <p className="text-gray-100">{memory.content}</p>
                <div className="flex gap-2 mt-3">
                  <span className="px-2 py-1 rounded bg-purple-600/20 text-purple-400 text-xs">
                    {memory.type}
                  </span>
                  <span className="px-2 py-1 rounded bg-blue-600/20 text-blue-400 text-xs">
                    {memory.scope}
                  </span>
                  <span className="px-2 py-1 rounded bg-gray-700 text-gray-300 text-xs">
                    {memory.status}
                  </span>
                </div>
              </div>
              <div className="text-right text-sm text-gray-500">
                <p>Confidence: {(memory.confidence * 100).toFixed(0)}%</p>
                <p>Observations: {memory.observationCount}</p>
              </div>
            </div>
            {memory.tags?.length > 0 && (
              <div className="flex gap-2 mt-3">
                {memory.tags.map((tag) => (
                  <span key={tag} className="px-2 py-1 rounded bg-gray-800 text-gray-400 text-xs">
                    #{tag}
                  </span>
                ))}
              </div>
            )}
          </div>
        ))}
        {memories?.length === 0 && (
          <p className="text-gray-500 text-center py-8">No memories found</p>
        )}
      </div>
    </div>
  );
}