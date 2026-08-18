import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Bot, Terminal, AlertTriangle, CheckCircle, ShieldAlert, Sparkles, BookOpen, Clock, FileCode, Layers, Bookmark } from 'lucide-react';

export default function AgentActivity() {
  const [activeTab, setActiveTab] = useState('attempts'); // 'attempts' | 'timeline' | 'sessions' | 'checkpoints'

  const { data: attempts, isLoading: attemptsLoading } = useQuery({
    queryKey: ['attempts'],
    queryFn: () => brainApi.getAgentAttempts(),
  });

  const { data: events, isLoading: eventsLoading } = useQuery({
    queryKey: ['events'],
    queryFn: () => brainApi.getRecentEvents(),
  });

  const { data: sessions, isLoading: sessionsLoading } = useQuery({
    queryKey: ['sessions'],
    queryFn: () => brainApi.getRecentSessions(),
  });

  const attemptList = Array.isArray(attempts) ? attempts : [];
  const eventList = Array.isArray(events) ? events : [];
  const sessionList = Array.isArray(sessions) ? sessions : [];
  const checkpointEvents = eventList.filter(e => e.eventType === 'SESSION_CHECKPOINT');

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white flex items-center gap-3">
            <Bot className="w-7 h-7 text-indigo-400" />
            Autonomous Agent Bridge & Continuity
          </h2>
          <p className="text-gray-400 text-sm mt-1">
            Real-time multi-agent activity capture, failed trial memory, and cross-agent state bridge (Claude Code ↔ Codex ↔ Cursor).
          </p>
        </div>

        {/* Navigation Tabs */}
        <div className="flex bg-gray-900 border border-gray-800 rounded-lg p-1">
          <button
            onClick={() => setActiveTab('attempts')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'attempts' ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            Engineering Attempts ({attemptList.length})
          </button>
          <button
            onClick={() => setActiveTab('timeline')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'timeline' ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            Live Event Stream ({eventList.length})
          </button>
          <button
            onClick={() => setActiveTab('sessions')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'sessions' ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            Sessions ({sessionList.length})
          </button>
          <button
            onClick={() => setActiveTab('checkpoints')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'checkpoints' ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            <Bookmark className="w-3.5 h-3.5" />
            Checkpoints ({checkpointEvents.length})
          </button>
        </div>
      </div>

      {/* TAB 1: ENGINEERING ATTEMPTS & TRIALS */}
      {activeTab === 'attempts' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-semibold text-gray-100 flex items-center gap-2">
                <Sparkles className="w-5 h-5 text-indigo-400" />
                Engineering Trial & Error Memory
              </h3>
              <p className="text-xs text-gray-400">
                Remembers previous approaches tried by Claude, Codex, or humans so future agents don't repeat failed attempts.
              </p>
            </div>
            <span className="text-xs bg-indigo-950/60 text-indigo-400 border border-indigo-800/60 px-2.5 py-1 rounded-full font-mono">
              {attemptList.length} recorded
            </span>
          </div>

          {attemptsLoading ? (
            <div className="text-gray-500 text-sm py-8 text-center">Loading engineering trials...</div>
          ) : attemptList.length > 0 ? (
            <div className="space-y-4">
              {attemptList.map((att) => (
                <div
                  key={att.id}
                  className={`border rounded-xl p-5 transition ${
                    att.status === 'FAILURE'
                      ? 'bg-red-950/20 border-red-900/40 hover:border-red-700/60'
                      : att.status === 'SUCCESS'
                      ? 'bg-green-950/20 border-green-900/40 hover:border-green-700/60'
                      : 'bg-gray-800/60 border-gray-700/60 hover:border-gray-600'
                  }`}
                >
                  <div className="flex items-start justify-between gap-4 mb-3">
                    <div>
                      <div className="flex items-center gap-2.5 mb-1">
                        <span className="font-semibold text-gray-100 text-base">{att.taskDescription}</span>
                        <span
                          className={`text-xs px-2.5 py-0.5 rounded-full font-medium flex items-center gap-1.5 ${
                            att.status === 'FAILURE'
                              ? 'bg-red-900/60 text-red-300 border border-red-700/50'
                              : att.status === 'SUCCESS'
                              ? 'bg-green-900/60 text-green-300 border border-green-700/50'
                              : 'bg-yellow-900/60 text-yellow-300 border border-yellow-700/50'
                          }`}
                        >
                          {att.status === 'FAILURE' ? (
                            <ShieldAlert className="w-3.5 h-3.5" />
                          ) : (
                            <CheckCircle className="w-3.5 h-3.5" />
                          )}
                          {att.status}
                        </span>
                      </div>
                      <p className="text-xs text-gray-400 font-mono">
                        Attempted by <span className="text-indigo-300 font-bold">{att.agentName}</span> at{' '}
                        {att.createdAt ? new Date(att.createdAt).toLocaleString() : ''}
                      </p>
                    </div>
                  </div>

                  {/* Approach */}
                  <div className="mb-3 bg-gray-900/70 rounded-lg p-3 border border-gray-800">
                    <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold block mb-1">Approach Attempted:</span>
                    <p className="text-sm text-gray-200">{att.approach}</p>
                  </div>

                  {/* Error Message if Failed */}
                  {att.errorMessage && (
                    <div className="mb-3 bg-red-950/40 border border-red-900/60 rounded-lg p-3 text-red-200">
                      <span className="text-xs text-red-400 font-semibold flex items-center gap-1 mb-1">
                        <AlertTriangle className="w-3.5 h-3.5" /> Failure / Error Encountered:
                      </span>
                      <pre className="text-xs font-mono whitespace-pre-wrap">{att.errorMessage}</pre>
                    </div>
                  )}

                  {/* Lesson Learned */}
                  {att.lessonLearned && (
                    <div className="bg-indigo-950/30 border border-indigo-900/50 rounded-lg p-3 text-indigo-200">
                      <span className="text-xs text-indigo-400 font-semibold flex items-center gap-1 mb-1">
                        <Sparkles className="w-3.5 h-3.5" /> Key Lesson / Recommendation:
                      </span>
                      <p className="text-sm">{att.lessonLearned}</p>
                    </div>
                  )}

                  {/* Files Touched */}
                  {att.filesChanged && att.filesChanged.length > 0 && (
                    <div className="mt-3 flex items-center gap-2 flex-wrap">
                      <span className="text-xs text-gray-500 flex items-center gap-1">
                        <FileCode className="w-3 h-3" /> Files:
                      </span>
                      {att.filesChanged.map((f, i) => (
                        <span key={i} className="text-xs bg-gray-900 text-gray-300 px-2 py-0.5 rounded border border-gray-800 font-mono">
                          {f}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-12 border border-dashed border-gray-800 rounded-xl">
              <BookOpen className="w-10 h-10 text-gray-600 mx-auto mb-3" />
              <p className="text-gray-400 text-sm font-medium">No engineering attempts recorded yet</p>
              <p className="text-gray-600 text-xs mt-1">
                Run <code className="text-indigo-400">brain watch</code> in your CLI or have Claude Code / Codex record trials via MCP.
              </p>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: LIVE EVENT STREAM */}
      {activeTab === 'timeline' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-100 flex items-center gap-2">
            <Terminal className="w-5 h-5 text-indigo-400" />
            Autonomous Agent Event Stream
          </h3>
          {eventsLoading ? (
            <div className="text-gray-500 text-sm py-4">Loading timeline...</div>
          ) : eventList.length > 0 ? (
            <div className="relative">
              <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-gray-800" />
              <div className="space-y-6">
                {eventList.map((event) => (
                  <div key={event.id} className="relative pl-10">
                    <div className={`absolute left-2.5 top-1 w-3 h-3 rounded-full ${event.eventType === 'SESSION_CHECKPOINT' ? 'bg-amber-500' : 'bg-indigo-500'}`} />
                    <div className={`border rounded-lg p-4 ${event.eventType === 'SESSION_CHECKPOINT' ? 'bg-amber-950/20 border-amber-900/40' : 'bg-gray-800/80 border-gray-700/60'}`}>
                      <div className="flex items-center gap-2 mb-2 flex-wrap">
                        <span className={`px-2 py-1 rounded text-xs font-mono font-semibold ${event.eventType === 'SESSION_CHECKPOINT' ? 'bg-amber-950 text-amber-300 border border-amber-800/60' : 'bg-indigo-950 text-indigo-300 border border-indigo-800/60'}`}>
                          {event.eventType === 'SESSION_CHECKPOINT' ? 'CHECKPOINT' : event.eventType}
                        </span>
                        <span className="text-xs text-gray-400 flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {event.createdAt ? new Date(event.createdAt).toLocaleString() : ''}
                        </span>
                      </div>
                      <p className="text-gray-200 text-sm">{event.description}</p>
                      {event.filePath && (
                        <p className="text-xs text-indigo-300 mt-2 font-mono bg-gray-900 p-2 rounded border border-gray-800">
                          {event.filePath}
                        </p>
                      )}
                      {event.details && (
                        <pre className="text-xs text-gray-400 mt-2 font-mono bg-gray-900 p-2 rounded border border-gray-800 overflow-x-auto whitespace-pre-wrap">
                          {event.details}
                        </pre>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <p className="text-gray-500 text-sm">No recent agent activity recorded</p>
          )}
        </div>
      )}

      {/* TAB 3: ACTIVE SESSIONS */}
      {activeTab === 'sessions' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-100 flex items-center gap-2">
            <Layers className="w-5 h-5 text-indigo-400" />
            Agent Sessions & Handoff State
          </h3>
          {sessionsLoading ? (
            <div className="text-gray-500 text-sm py-4">Loading sessions...</div>
          ) : sessionList.length > 0 ? (
            <div className="space-y-3">
              {sessionList.map((session) => (
                <div key={session.id} className="flex items-center gap-4 p-4 bg-gray-800/70 border border-gray-700/60 rounded-xl">
                  <Bot className="w-8 h-8 text-indigo-400 flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-gray-100 truncate">{session.agent?.name || 'Autonomous Agent'}</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      Session ID: <span className="font-mono text-gray-300">{session.id}</span>
                    </p>
                  </div>
                  <div className="text-right text-sm flex-shrink-0">
                    <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-green-950 text-green-400 border border-green-800/60">
                      Active
                    </span>
                    <p className="text-gray-500 text-xs mt-1">
                      {session.startedAt ? new Date(session.startedAt).toLocaleString() : ''}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-gray-500 text-sm">No agent sessions active</p>
          )}
        </div>
      )}

      {/* TAB 4: SESSION CHECKPOINTS */}
      {activeTab === 'checkpoints' && (
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-100 flex items-center gap-2">
            <Bookmark className="w-5 h-5 text-indigo-400" />
            Session Checkpoints & Crash Recovery
          </h3>
          <p className="text-xs text-gray-400 mb-4">
            Intermediate session snapshots for instant crash recovery and handoff preservation. Each checkpoint captures progress, blockers, and modified files.
          </p>
          {eventsLoading ? (
            <div className="text-gray-500 text-sm py-4">Loading checkpoints...</div>
          ) : checkpointEvents.length > 0 ? (
            <div className="space-y-4">
              {checkpointEvents.map((event) => (
                <div key={event.id} className="border border-amber-900/40 bg-amber-950/20 rounded-xl p-5 hover:border-amber-700/60 transition">
                  <div className="flex items-start justify-between gap-4 mb-3">
                    <div>
                      <div className="flex items-center gap-2.5 mb-1">
                        <span className="font-semibold text-gray-100 text-base">Session Checkpoint</span>
                        <span className="text-xs px-2.5 py-0.5 rounded-full font-medium bg-amber-900/60 text-amber-300 border border-amber-700/50 flex items-center gap-1.5">
                          <Bookmark className="w-3.5 h-3.5" />
                          CHECKPOINT
                        </span>
                      </div>
                      <p className="text-xs text-gray-400 font-mono">
                        {event.createdAt ? new Date(event.createdAt).toLocaleString() : ''}
                      </p>
                    </div>
                  </div>
                  
                  {event.description && (
                    <div className="mb-3 bg-gray-900/70 rounded-lg p-3 border border-gray-800">
                      <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold block mb-1">Description:</span>
                      <p className="text-sm text-gray-200">{event.description}</p>
                    </div>
                  )}
                  
                  {event.details && (
                    <div className="bg-gray-900/70 border border-gray-800 rounded-lg p-3">
                      <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold block mb-1">Modified Files:</span>
                      <div className="flex items-center gap-2 flex-wrap mt-2">
                        {event.details.split(', ').map((file, i) => (
                          <span key={i} className="text-xs bg-gray-900 text-gray-300 px-2 py-0.5 rounded border border-gray-800 font-mono">
                            {file}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-12 border border-dashed border-gray-800 rounded-xl">
              <Bookmark className="w-10 h-10 text-gray-600 mx-auto mb-3" />
              <p className="text-gray-400 text-sm font-medium">No session checkpoints recorded yet</p>
              <p className="text-gray-600 text-xs mt-1">
                Checkpoints are created automatically via <code className="text-amber-400">brain_checkpoint_session</code> MCP tool during agent sessions.
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}