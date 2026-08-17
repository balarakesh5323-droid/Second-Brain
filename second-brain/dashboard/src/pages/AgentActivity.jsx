import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Bot } from 'lucide-react';

export default function AgentActivity() {
  const { data: events, isLoading: eventsLoading } = useQuery({
    queryKey: ['events'],
    queryFn: () => brainApi.getRecentEvents(),
  });

  const { data: sessions, isLoading: sessionsLoading } = useQuery({
    queryKey: ['sessions'],
    queryFn: () => brainApi.getRecentSessions(),
  });

  const eventList = Array.isArray(events) ? events : [];
  const sessionList = Array.isArray(sessions) ? sessions : [];

  return (
    <div className="space-y-8">
      <h2 className="text-2xl font-bold">Agent Activity</h2>
      
      {/* Timeline */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold mb-4">Event Timeline</h3>
        {eventsLoading ? (
          <div className="text-gray-500 text-sm py-4">Loading timeline...</div>
        ) : eventList.length > 0 ? (
          <div className="relative">
            <div className="absolute left-4 top-0 bottom-0 w-0.5 bg-gray-800" />
            <div className="space-y-6">
              {eventList.map((event) => (
                <div key={event.id} className="relative pl-10">
                  <div className="absolute left-2.5 top-1 w-3 h-3 rounded-full bg-purple-500" />
                  <div className="bg-gray-800 rounded-lg p-4">
                    <div className="flex items-center gap-2 mb-2 flex-wrap">
                      <span className="px-2 py-1 rounded bg-gray-700 text-xs font-mono">
                        {event.eventType}
                      </span>
                      <span className="text-xs text-gray-500">
                        {event.createdAt ? new Date(event.createdAt).toLocaleString() : ''}
                      </span>
                    </div>
                    <p className="text-gray-200">{event.description}</p>
                    {event.filePath && (
                      <p className="text-sm text-gray-400 mt-2 font-mono">{event.filePath}</p>
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

      {/* Sessions */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold mb-4">Recent Sessions</h3>
        {sessionsLoading ? (
          <div className="text-gray-500 text-sm py-4">Loading sessions...</div>
        ) : sessionList.length > 0 ? (
          <div className="space-y-3">
            {sessionList.map((session) => (
              <div key={session.id} className="flex items-center gap-4 p-4 bg-gray-800 rounded-lg">
                <Bot className="w-8 h-8 text-purple-400 flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="font-medium truncate">{session.agent?.name || 'Unknown Agent'}</p>
                  <p className="text-sm text-gray-400 line-clamp-1">{session.task}</p>
                </div>
                <div className="text-right text-sm flex-shrink-0">
                  <span className={`px-2 py-1 rounded text-xs ${
                    session.status === 'active' ? 'bg-green-600/20 text-green-400' : 'bg-gray-700 text-gray-400'
                  }`}>
                    {session.status}
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
    </div>
  );
}