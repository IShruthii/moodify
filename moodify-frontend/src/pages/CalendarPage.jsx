import React, { useEffect, useState } from 'react'
import Layout from '../components/common/Layout'
import CalendarWidget from '../components/calendar/CalendarWidget'
import MoodHistoryCard from '../components/mood/MoodHistoryCard'
import ChatbotFAB from '../components/chatbot/ChatbotFAB'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { getAnalytics } from '../api/analyticsApi'
import { getMoodHistory } from '../api/moodApi'
import { getFeedbackHistory } from '../api/feedbackApi'
import './CalendarPage.css'

export default function CalendarPage() {
  const [analytics, setAnalytics] = useState(null)
  const [history,   setHistory]   = useState([])
  const [feedbackData, setFeedbackData] = useState({})
  const [loading,   setLoading]   = useState(true)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [analyticsRes, historyRes, feedbackRes] = await Promise.all([
          getAnalytics(),
          getMoodHistory(),
          getFeedbackHistory(),
        ])
        setAnalytics(analyticsRes.data.data)
        setHistory(historyRes.data.data || [])

        // Build feedbackData map: date -> rating
        const fbMap = {}
        for (const f of (feedbackRes.data.data || [])) {
          if (f.createdAt) {
            const date = f.createdAt.split('T')[0]
            // Keep highest rating for the day
            if (!fbMap[date] || f.rating > fbMap[date]) fbMap[date] = f.rating
          }
        }
        setFeedbackData(fbMap)
      } catch {
        // silent
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  if (loading) {
    return (
      <Layout>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
          <LoadingSpinner size="lg" text="Loading your mood history..." />
        </div>
      </Layout>
    )
  }

  return (
    <Layout>
      <div className="calendar-page animate-fade-in">
        <div className="calendar-page-header">
          <h1 className="page-title">Mood Calendar</h1>
          <p className="page-subtitle">Your emotional journey over time</p>
        </div>

        <div className="calendar-layout">
          <div className="calendar-main">
            <CalendarWidget calendarData={analytics?.calendarData || {}} feedbackData={feedbackData} moodHistory={history} />
          </div>

          <div className="calendar-sidebar">
            <div className="cal-stat-card">
              <span className="cal-stat-icon">🔥</span>
              <div>
                <div className="cal-stat-value">{analytics?.currentStreak || 0}</div>
                <div className="cal-stat-label">Current Streak</div>
              </div>
            </div>
            <div className="cal-stat-card">
              <span className="cal-stat-icon">👑</span>
              <div>
                <div className="cal-stat-value">{analytics?.longestStreak || 0}</div>
                <div className="cal-stat-label">Best Streak</div>
              </div>
            </div>
            <div className="cal-stat-card">
              <span className="cal-stat-icon">📊</span>
              <div>
                <div className="cal-stat-value">{analytics?.totalEntries || 0}</div>
                <div className="cal-stat-label">Total Entries</div>
              </div>
            </div>
          </div>
        </div>

        <div className="history-section">
          <h2 className="section-title">Recent Entries</h2>
          {history.length === 0 ? (
            <div className="history-empty">
              <span>📭</span>
              <p>No mood entries yet. Start logging your mood!</p>
            </div>
          ) : (
            <div className="history-list">
              {history.slice(0, 20).map(entry => (
                <MoodHistoryCard key={entry.id} entry={entry} />
              ))}
            </div>
          )}
        </div>
      </div>
      <ChatbotFAB />
    </Layout>
  )
}
