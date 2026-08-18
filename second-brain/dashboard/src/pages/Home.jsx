import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Activity, Database, FolderGit2, Users, CheckCircle, Clock, Bookmark } from 'lucide-react';

function StatCard({ icon: Icon, label, value, color }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
      <div className="flex items-center gap-4">
        <div className={`p-3 rounded-lg ${color}`}>
          <Icon className="w-6 h-6" />
        </div>
        <div>
          <p className="text-sm text-gray-400">{label}</p>
          <p className="text-2xl font-bold">{value}</p>
        </div>
      </div>
    </div>
  );
}

export default function Home() {
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => brainApi.getProjects() });
  const { data: memories } = useQuery({ queryKey: ['memories'], queryFn: () => brainApi.getMemories() });
  const { data: agents } = useQuery({ queryKey: ['agents'], queryFn: () => brainApi.getAgents() });
  const { data: tasks } = useQuery({ queryKey: ['tasks'], queryFn: () => brainApi.getOpenTasks() });
  const { data: events } = useQuery({ queryKey: ['events'], queryFn: () => brainApi.getRecentEvents() });
  const { data: decisions } = useQuery({ queryKey: ['decisions'], queryFn: () => brainApi.getRecentDecisions() });

  const projectList = Array.isArray(projects) ? projects : [];
  const memoryList = Array.isArray(memories) ? memories : [];
  const agentList = Array.isArray(agents) ? agents : [];
  const taskList = Array.isArray(tasks) ? tasks : [];
  const eventList = Array.isArray(events) ? events : [];
  const decisionList = Array.isArray(decisions) ? decisions : [];

  return (
    <div className="space-y-8">
      <h2 className="text-2xl font-bold">Dashboard</h2>
      
      {/* Stats grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard icon={FolderGit2} label="Projects" value={projectList.length} color="bg-blue-600/20 text-blue-400" />
        <StatCard icon={Database} label="Memories" value={memoryList.length} color="bg-purple-600/20 text-purple-400" />
        <StatCard icon={Users} label="Agents" value={agentList.length} color="bg-green-600/20 text-green-400" />
        <StatCard icon={CheckCircle} label="Open Tasks" value={taskList.length} color="bg-yellow-600/20 text-yellow-400" />
        <StatCard icon={Activity} label="Recent Events" value={eventList.length} color="bg-red-600/20 text-red-400" />
        <StatCard icon={Clock} label="Decisions" value={decisionList.length} color="bg-cyan-600/20 text-cyan-400" />
        <StatCard icon={Bookmark} label="Checkpoints" value={eventList.filter(e => e.eventType === 'SESSION_CHECKPOINT').length} color="bg-amber-600/20 text-amber-400" />
      </div>

      {/* Recent Activity */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold mb-4">Recent Activity</h3>
        {eventList.length > 0 ? (
          <div className="space-y-3">
            {eventList.slice(0, 10).map((event) => (
              <div key={event.id} className={`flex items-center gap-3 text-sm ${event.eventType === 'SESSION_CHECKPOINT' ? 'bg-amber-950/20 border border-amber-900/30 rounded-lg p-3' : ''}`}>
                <span className={`px-2 py-1 rounded text-xs font-mono ${event.eventType === 'SESSION_CHECKPOINT' ? 'bg-amber-900/60 text-amber-300 border border-amber-700/50' : 'bg-gray-800 text-gray-300'}`}>
                  {event.eventType === 'SESSION_CHECKPOINT' ? 'CHECKPOINT' : event.eventType}
                </span>
                <span className="text-gray-300">{event.description}</span>
                <span className="text-gray-500 ml-auto text-xs">
                  {event.createdAt ? new Date(event.createdAt).toLocaleString() : ''}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-gray-500">No recent activity</p>
        )}
      </div>
    </div>
  );
}