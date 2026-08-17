import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Activity, Database, FolderGit2, Users, CheckCircle, Clock } from 'lucide-react';

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
  const { data: projects } = useQuery({ queryKey: ['projects'], queryFn: () => brainApi.getProjects().then(r => r.data) });
  const { data: memories } = useQuery({ queryKey: ['memories'], queryFn: () => brainApi.getMemories().then(r => r.data) });
  const { data: agents } = useQuery({ queryKey: ['agents'], queryFn: () => brainApi.getAgents().then(r => r.data) });
  const { data: tasks } = useQuery({ queryKey: ['tasks'], queryFn: () => brainApi.getOpenTasks().then(r => r.data) });
  const { data: events } = useQuery({ queryKey: ['events'], queryFn: () => brainApi.getRecentEvents().then(r => r.data) });
  const { data: decisions } = useQuery({ queryKey: ['decisions'], queryFn: () => brainApi.getRecentDecisions().then(r => r.data) });

  return (
    <div className="space-y-8">
      <h2 className="text-2xl font-bold">Dashboard</h2>
      
      {/* Stats grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard icon={FolderGit2} label="Projects" value={projects?.length || 0} color="bg-blue-600/20 text-blue-400" />
        <StatCard icon={Database} label="Memories" value={memories?.length || 0} color="bg-purple-600/20 text-purple-400" />
        <StatCard icon={Users} label="Agents" value={agents?.length || 0} color="bg-green-600/20 text-green-400" />
        <StatCard icon={CheckCircle} label="Open Tasks" value={tasks?.length || 0} color="bg-yellow-600/20 text-yellow-400" />
        <StatCard icon={Activity} label="Recent Events" value={events?.length || 0} color="bg-red-600/20 text-red-400" />
        <StatCard icon={Clock} label="Decisions" value={decisions?.length || 0} color="bg-cyan-600/20 text-cyan-400" />
      </div>

      {/* Recent Activity */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h3 className="text-lg font-semibold mb-4">Recent Activity</h3>
        {events?.length > 0 ? (
          <div className="space-y-3">
            {events.slice(0, 10).map((event) => (
              <div key={event.id} className="flex items-center gap-3 text-sm">
                <span className="px-2 py-1 rounded bg-gray-800 text-gray-300 text-xs font-mono">
                  {event.eventType}
                </span>
                <span className="text-gray-300">{event.description}</span>
                <span className="text-gray-500 ml-auto text-xs">
                  {new Date(event.createdAt).toLocaleString()}
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