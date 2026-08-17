import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import {
  Search,
  Brain,
  Code2,
  RefreshCw,
  Tag,
  Loader2,
  CheckCircle2,
  AlertCircle,
  Sparkles,
  Layers
} from 'lucide-react';

export default function MemoryExplorer() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [searchMode, setSearchMode] = useState('memories'); // 'memories' | 'symbols'
  const [isConsolidating, setIsConsolidating] = useState(false);
  const [consolidationReport, setConsolidationReport] = useState(null);

  const { data: searchResults, isLoading: searchLoading } = useQuery({
    queryKey: ['memorySearch', searchTerm, searchMode],
    queryFn: () => searchMode === 'symbols' ? brainApi.searchSymbols(searchTerm) : brainApi.searchMemory(searchTerm),
    enabled: !!searchTerm,
  });

  const { data: allMemories, isLoading: allLoading } = useQuery({
    queryKey: ['memories'],
    queryFn: () => brainApi.getMemories(),
    enabled: !searchTerm && searchMode === 'memories',
  });

  const rawMemories = searchTerm ? searchResults : allMemories;
  const memoryList = Array.isArray(rawMemories) ? rawMemories : [];
  const isLoading = searchTerm ? searchLoading : allLoading;

  const handleConsolidate = async () => {
    setIsConsolidating(true);
    setConsolidationReport(null);
    try {
      const res = await brainApi.consolidateMemories();
      setConsolidationReport({
        success: true,
        text: `Consolidated! ${res.contradictionsResolved || 0} contradictions resolved, ${res.memoriesCompounded || 0} compounded, ${res.eventsConsolidated || 0} events synthesized.`,
      });
      queryClient.invalidateQueries({ queryKey: ['memories'] });
    } catch (err) {
      setConsolidationReport({
        success: false,
        text: `Consolidation failed: ${err.message}`,
      });
    } finally {
      setIsConsolidating(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">Memory &amp; Symbol Explorer</h2>
          <p className="text-xs text-gray-400 mt-0.5">
            Declarative rules, architectural patterns, and fine-grained code symbol vectors
          </p>
        </div>

        <button
          onClick={handleConsolidate}
          disabled={isConsolidating}
          className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-purple-950/40 hover:bg-purple-900/60 border border-purple-800/60 hover:border-purple-600 text-purple-300 text-xs font-semibold transition-all cursor-pointer shadow-sm disabled:opacity-50"
          title="Run autonomous contradiction resolution, confidence compounding, and event consolidation"
        >
          {isConsolidating ? (
            <Loader2 className="w-4 h-4 animate-spin text-purple-400" />
          ) : (
            <Sparkles className="w-4 h-4 text-purple-400" />
          )}
          <span>{isConsolidating ? 'Consolidating...' : 'Consolidate Memories'}</span>
        </button>
      </div>

      {/* Consolidation Result Toast */}
      {consolidationReport && (
        <div
          className={`p-3 rounded-lg border text-xs flex items-center justify-between ${
            consolidationReport.success
              ? 'bg-emerald-950/40 border-emerald-800 text-emerald-300'
              : 'bg-red-950/40 border-red-800 text-red-300'
          }`}
        >
          <span>{consolidationReport.text}</span>
          <button
            onClick={() => setConsolidationReport(null)}
            className="text-gray-400 hover:text-gray-200 cursor-pointer ml-3 font-bold"
          >
            &times;
          </button>
        </div>
      )}

      {/* Search Bar & Mode Switch */}
      <div className="space-y-3">
        <div className="flex gap-2">
          <button
            onClick={() => { setSearchMode('memories'); setSearchTerm(''); setQuery(''); }}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer flex items-center gap-1.5 ${
              searchMode === 'memories'
                ? 'bg-purple-600 text-white'
                : 'bg-gray-900 border border-gray-800 text-gray-400 hover:text-gray-200'
            }`}
          >
            <Brain className="w-3.5 h-3.5" />
            <span>Memories &amp; Rules</span>
          </button>
          <button
            onClick={() => { setSearchMode('symbols'); setSearchTerm(''); setQuery(''); }}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer flex items-center gap-1.5 ${
              searchMode === 'symbols'
                ? 'bg-purple-600 text-white'
                : 'bg-gray-900 border border-gray-800 text-gray-400 hover:text-gray-200'
            }`}
          >
            <Code2 className="w-3.5 h-3.5" />
            <span>Symbol Code-RAG Search</span>
          </button>
        </div>

        <div className="flex gap-3">
          <div className="flex-1 relative">
            <Search className="absolute left-3 top-3 w-5 h-5 text-gray-500" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && setSearchTerm(query)}
              placeholder={
                searchMode === 'symbols'
                  ? "Search code symbols (e.g., 'validateAuth', 'jwtToken', 'findUsers')..."
                  : "Search declarative memories, decisions, and patterns..."
              }
              className="w-full pl-10 pr-4 py-3 bg-gray-900 border border-gray-700 rounded-lg text-gray-100 placeholder-gray-500 focus:outline-none focus:border-purple-500"
            />
          </div>
          <button
            onClick={() => setSearchTerm(query)}
            className="px-6 py-3 bg-purple-600 hover:bg-purple-700 rounded-lg font-medium transition-colors cursor-pointer text-sm"
          >
            Search
          </button>
          {searchTerm && (
            <button
              onClick={() => { setQuery(''); setSearchTerm(''); }}
              className="px-4 py-3 bg-gray-800 hover:bg-gray-700 rounded-lg transition-colors cursor-pointer text-sm text-gray-300"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Results List */}
      <div className="space-y-3">
        {isLoading ? (
          <div className="text-gray-500 text-sm py-8 text-center flex items-center justify-center gap-2">
            <Loader2 className="w-4 h-4 animate-spin text-purple-400" />
            <span>Loading...</span>
          </div>
        ) : memoryList.length === 0 ? (
          <div className="text-gray-500 text-sm text-center py-12 bg-gray-900/40 rounded-xl border border-gray-800">
            {searchTerm ? 'No matching results found.' : 'No memories stored yet.'}
          </div>
        ) : (
          memoryList.map((item) => {
            const tags = Array.isArray(item.tags)
              ? item.tags
              : item.tags instanceof Set
              ? Array.from(item.tags)
              : [];

            return (
              <div
                key={item.id}
                className="bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors space-y-3"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    {searchMode === 'symbols' || item.payload?.type === 'function' ? (
                      <div className="space-y-2 font-mono">
                        <div className="flex items-center gap-2">
                          <span className="px-2 py-0.5 rounded bg-emerald-950 text-emerald-400 border border-emerald-800 text-xs font-bold">
                            FUNCTION
                          </span>
                          <span className="text-sm text-yellow-300 font-bold">
                            {item.payload?.name || item.name}
                          </span>
                          <span className="text-xs text-gray-400">
                            ({item.payload?.parameters || item.parameters || ''}): {item.payload?.returnType || item.returnType || 'void'}
                          </span>
                        </div>
                        <p className="text-xs text-gray-400 font-sans">
                          {item.payload?.docstring || item.docstring || item.content}
                        </p>
                        <div className="text-[11px] text-gray-500">
                          File: <span className="text-gray-300">{item.payload?.file || item.file}</span>
                        </div>
                      </div>
                    ) : (
                      <p className="text-gray-100 text-sm leading-relaxed">{item.content}</p>
                    )}
                  </div>

                  <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
                    {item.score != null && (
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-950 text-purple-300 border border-purple-800/60">
                        Score: {(item.score * 100).toFixed(0)}%
                      </span>
                    )}
                    {item.type && (
                      <span className="text-xs px-2.5 py-0.5 rounded-full bg-purple-600/20 text-purple-400 font-medium">
                        {item.type}
                      </span>
                    )}
                    {item.status && (
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-gray-800 text-gray-300">
                        {item.status}
                      </span>
                    )}
                  </div>
                </div>

                {tags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 pt-1">
                    {tags.map((t, idx) => (
                      <span
                        key={idx}
                        className="px-2 py-0.5 rounded bg-gray-950 text-gray-400 border border-gray-800 text-xs font-mono"
                      >
                        #{t}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}