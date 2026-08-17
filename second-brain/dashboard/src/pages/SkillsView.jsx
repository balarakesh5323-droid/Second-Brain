import { useQuery } from '@tanstack/react-query';
import { brainApi } from '../api/client';
import { Zap, TrendingUp } from 'lucide-react';

export default function SkillsView() {
  const { data: skills } = useQuery({
    queryKey: ['skills'],
    queryFn: () => brainApi.getSkills().then(r => r.data),
  });

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Skills</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {skills?.map((skill) => (
          <div key={skill.id} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <div className="flex items-center gap-3 mb-3">
              <Zap className="w-6 h-6 text-yellow-400" />
              <h3 className="font-semibold">{skill.name}</h3>
            </div>
            <p className="text-sm text-gray-400 mb-3">{skill.description}</p>
            <div className="flex items-center gap-4 text-sm">
              <span className="flex items-center gap-1 text-gray-400">
                <TrendingUp className="w-4 h-4" />
                {skill.confidence ? `${(skill.confidence * 100).toFixed(0)}%` : 'N/A'}
              </span>
              <span className="text-gray-500">Used {skill.usageCount || 0} times</span>
            </div>
            {skill.triggers?.length > 0 && (
              <div className="flex gap-1 mt-3 flex-wrap">
                {skill.triggers.map((t) => (
                  <span key={t} className="px-2 py-1 rounded bg-gray-800 text-xs text-gray-400">{t}</span>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}