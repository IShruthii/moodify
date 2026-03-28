import React, { useState } from 'react'
import './CalendarWidget.css'

const MOOD_COLORS = {
  HAPPY: '#f59e0b', EXCITED: '#f97316', MOTIVATED: '#10b981',
  CONFIDENT: '#3b82f6', HOPEFUL: '#8b5cf6', PEACEFUL: '#14b8a6',
  RELAXED: '#6366f1', CALM: '#0ea5e9', NEUTRAL: '#6b7280',
  TIRED: '#78716c', BORED: '#9ca3af', LONELY: '#a78bfa',
  INSECURE: '#c084fc', SAD: '#60a5fa', DISAPPOINTED: '#818cf8',
  ANXIOUS: '#fb923c', STRESSED: '#f87171', OVERWHELMED: '#ef4444',
  FRUSTRATED: '#f43f5e', ANGRY: '#dc2626',
}

const MOOD_EMOJIS = {
  HAPPY: '😊', EXCITED: '🤩', MOTIVATED: '💪', CONFIDENT: '😎',
  HOPEFUL: '🌟', PEACEFUL: '🕊️', RELAXED: '😌', CALM: '🧘',
  NEUTRAL: '😐', TIRED: '😴', BORED: '😑', LONELY: '🥺',
  INSECURE: '😔', SAD: '😢', DISAPPOINTED: '😞', ANXIOUS: '😟',
  STRESSED: '😰', OVERWHELMED: '🤯', FRUSTRATED: '😤', ANGRY: '😠',
}

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTHS = ['January','February','March','April','May','June',
                 'July','August','September','October','November','December']

function DayModal({ dateStr, mood, feedbackRating, moodHistory, onClose }) {
  const entry = moodHistory?.find(e => e.entryDate === dateStr || (e.createdAt && e.createdAt.startsWith(dateStr)))
  const [d, m, y] = dateStr.split('-').reverse().map(Number)
  const label = `${MONTHS[m - 1]} ${d}, ${y}`

  return (
    <div className="cal-modal-overlay" onClick={onClose}>
      <div className="cal-modal animate-fade-in" onClick={e => e.stopPropagation()}>
        <button className="cal-modal-close" onClick={onClose}>✕</button>
        <div className="cal-modal-date">{label}</div>

        {mood ? (
          <>
            <div className="cal-modal-mood-emoji">{MOOD_EMOJIS[mood] || '😐'}</div>
            <div className="cal-modal-mood-name" style={{ color: MOOD_COLORS[mood] || '#7c3aed' }}>
              {mood.charAt(0) + mood.slice(1).toLowerCase()}
            </div>
            {entry?.note && (
              <div className="cal-modal-note">
                <span className="cal-modal-note-label">📝 Note</span>
                <p>{entry.note}</p>
              </div>
            )}
            {feedbackRating && (
              <div className="cal-modal-feedback">
                <span className="cal-modal-note-label">⭐ Session Rating</span>
                <div className="cal-modal-stars">
                  {[1,2,3,4,5].map(s => (
                    <span key={s} style={{ color: s <= feedbackRating ? '#f59e0b' : 'rgba(255,255,255,0.2)', fontSize: 20 }}>★</span>
                  ))}
                </div>
              </div>
            )}
            {entry?.energyLevel != null && (
              <div className="cal-modal-meta">
                <span>⚡ Energy: {entry.energyLevel}/10</span>
                {entry.positivityScore != null && <span>✨ Positivity: {entry.positivityScore}/10</span>}
              </div>
            )}
          </>
        ) : (
          <div className="cal-modal-empty">
            <span>📭</span>
            <p>No mood logged for this day</p>
          </div>
        )}
      </div>
    </div>
  )
}

export default function CalendarWidget({ calendarData = {}, feedbackData = {}, moodHistory = [] }) {
  const today = new Date()
  const [viewDate, setViewDate] = useState(new Date(today.getFullYear(), today.getMonth(), 1))
  const [selectedDay, setSelectedDay] = useState(null)

  const year = viewDate.getFullYear()
  const month = viewDate.getMonth()
  const firstDay = new Date(year, month, 1).getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()

  const prevMonth = () => setViewDate(new Date(year, month - 1, 1))
  const nextMonth = () => setViewDate(new Date(year, month + 1, 1))

  const formatDate = (d) => {
    const mm = String(month + 1).padStart(2, '0')
    const dd = String(d).padStart(2, '0')
    return `${year}-${mm}-${dd}`
  }

  const cells = []
  for (let i = 0; i < firstDay; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) cells.push(d)

  return (
    <>
      <div className="calendar-widget">
        <div className="cal-header">
          <button className="cal-nav-btn" onClick={prevMonth}>‹</button>
          <span className="cal-title">{MONTHS[month]} {year}</span>
          <button className="cal-nav-btn" onClick={nextMonth}>›</button>
        </div>

        <div className="cal-days-header">
          {DAYS.map(d => <span key={d} className="cal-day-name">{d}</span>)}
        </div>

        <div className="cal-grid">
          {cells.map((day, i) => {
            if (!day) return <div key={`empty-${i}`} className="cal-cell empty" />
            const dateStr = formatDate(day)
            const mood = calendarData[dateStr]
            const feedbackRating = feedbackData[dateStr]
            const isToday = dateStr === today.toISOString().split('T')[0]

            return (
              <div
                key={dateStr}
                className={`cal-cell ${isToday ? 'today' : ''} ${mood ? 'has-mood' : ''} clickable`}
                style={mood ? { '--mood-color': MOOD_COLORS[mood] || '#7c3aed' } : {}}
                title={mood ? `${mood} ${MOOD_EMOJIS[mood] || ''}` : 'No entry'}
                onClick={() => setSelectedDay(dateStr)}
              >
                <span className="cal-day-num">{day}</span>
                {mood && <span className="cal-mood-dot">{MOOD_EMOJIS[mood] || '•'}</span>}
                {feedbackRating && (
                  <span className="cal-feedback-dot" style={{
                    '--fb-color': feedbackRating >= 4 ? '#10b981' : feedbackRating === 3 ? '#f59e0b' : '#f43f5e'
                  }}>
                    {'★'.repeat(Math.min(feedbackRating, 3))}
                  </span>
                )}
              </div>
            )
          })}
        </div>

        <div className="cal-legend">
          <span className="cal-legend-item"><span className="legend-dot positive" />Positive</span>
          <span className="cal-legend-item"><span className="legend-dot neutral" />Neutral</span>
          <span className="cal-legend-item"><span className="legend-dot negative" />Challenging</span>
        </div>
      </div>

      {selectedDay && (
        <DayModal
          dateStr={selectedDay}
          mood={calendarData[selectedDay]}
          feedbackRating={feedbackData[selectedDay]}
          moodHistory={moodHistory}
          onClose={() => setSelectedDay(null)}
        />
      )}
    </>
  )
}
