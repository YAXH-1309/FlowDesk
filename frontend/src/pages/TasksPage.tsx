import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../api/client'

type Task = { id: string; title: string; status: string; description?: string }
type Project = { id: string; name: string }

export default function TasksPage() {
  const qc = useQueryClient()
  const [selectedProject, setSelectedProject] = useState<string | null>(null)

  const { data: projects = [] } = useQuery<Project[]>({
    queryKey: ['projects'],
    queryFn: () => api.get('/tasks/projects').then((r) => r.data),
  })

  const { data: tasks = [] } = useQuery<Task[]>({
    queryKey: ['tasks', selectedProject],
    queryFn: () => api.get(`/tasks/projects/${selectedProject}/tasks`).then((r) => r.data),
    enabled: !!selectedProject,
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      api.put(`/tasks/tasks/${id}`, { status }),
    // Optimistic update
    onMutate: async ({ id, status }) => {
      await qc.cancelQueries({ queryKey: ['tasks', selectedProject] })
      const prev = qc.getQueryData<Task[]>(['tasks', selectedProject])
      qc.setQueryData<Task[]>(['tasks', selectedProject], (old) =>
        old?.map((t) => (t.id === id ? { ...t, status } : t)) ?? [])
      return { prev }
    },
    onError: (_err, _vars, ctx) => {
      qc.setQueryData(['tasks', selectedProject], ctx?.prev)
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['tasks', selectedProject] }),
  })

  const statuses = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']

  return (
    <main style={{ padding: '2rem' }}>
      <h1>Task Board</h1>
      <label htmlFor="project-select">Project</label>
      <select id="project-select" value={selectedProject ?? ''} onChange={(e) => setSelectedProject(e.target.value)}>
        <option value="">Select a project</option>
        {projects.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </select>

      {selectedProject && (
        <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }} role="region" aria-label="Kanban board">
          {statuses.map((status) => (
            <section key={status} aria-label={status} style={{ flex: 1, background: '#f5f5f5', padding: '1rem', borderRadius: 4 }}>
              <h2 style={{ fontSize: '1rem' }}>{status}</h2>
              {tasks.filter((t) => t.status === status).map((task) => (
                <article key={task.id} style={{ background: '#fff', padding: '0.5rem', marginBottom: '0.5rem', borderRadius: 4 }}>
                  <p>{task.title}</p>
                  <select
                    aria-label={`Move ${task.title}`}
                    value={task.status}
                    onChange={(e) => updateStatus.mutate({ id: task.id, status: e.target.value })}
                  >
                    {statuses.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </article>
              ))}
            </section>
          ))}
        </div>
      )}
    </main>
  )
}
