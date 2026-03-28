import React, { useState } from 'react'
import { submitFeedback } from '../../api/feedbackApi'
import './FeedbackModal.css'

const EMOJI_RATINGS = [
  { emoji: '😭', label: 'Terrible', value: 1 },
  { emoji: '😕', label: 'Not great', value: 2 },
  { emoji: '😐', label: 'Okay', value: 3 },
  { emoji: '😊', label: 'Good', value: 4 },
  { emoji: '🤩', label: 'Amazing!', value: 5 },
]

const PROMPTS = {
  1: "We're sorry it wasn't helpful. What could we do better?",
  2: "Thanks for sharing. What felt off?",
  3: "Good to know! What would make it better?",
  4: "Great! What did you enjoy most?",
  5: "Amazing! We're so glad it helped. Tell us more!",
}

export default function FeedbackModal({ moodBefore, moodAfter, sessionType = 'RECOMMENDATION', onDone, onSkip }) {
  const [rating,   setRating]   = useState(0)
  const [comment,  setComment]  = useState('')
  const [phase,    setPhase]    = useState('rate')
  const [saving,   setSaving]   = useState(false)

  const handleSubmit = async () => {
    if (!rating) return
    setSaving(true)
    try {
      await submitFeedback({ rating, comment, moodBefore, moodAfter, sessionType })
    } catch {
      // silent — still show thanks
    } finally {
      setSaving(false)
      setPhase('thanks')
    }
  }

  if (phase === 'thanks') {
    return (
      <div className="fb-overlay">
        <div className="fb-card animate-fade-in">
          <span className="fb-thanks-icon">🙏</span>
          <h2 className="fb-thanks-title">Thank you!</h2>
          <p className="fb-thanks-sub">
            Your feedback helps Moodify get better every day. We really appreciate it.
          </p>
          <button className="btn btn-primary btn-lg fb-cta" onClick={onDone}>
            Continue ✨
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="fb-overlay">
      <div className="fb-card animate-fade-in">

        <div className="fb-header">
          <span className="fb-header-icon">💬</span>
          <h2 className="fb-title">How was your session?</h2>
          <p className="fb-sub">Tap an emoji to rate your overall experience.</p>
        </div>

        {/* Mood transition if available */}
        {moodBefore && moodAfter && (
          <div className="fb-transition">
            <span className="fb-mood-chip">{moodBefore}</span>
            <span className="fb-arrow">→</span>
            <span className="fb-mood-chip after">{moodAfter}</span>
          </div>
        )}

        {/* Emoji rating */}
        <div className="fb-emoji-wrap">
          {EMOJI_RATINGS.map(({ emoji, label, value }) => (
            <button
              key={value}
              className={`fb-emoji-btn ${rating === value ? 'selected' : ''}`}
              onClick={() => setRating(value)}
              aria-label={label}
              title={label}
            >
              <span className="fb-emoji">{emoji}</span>
              <span className="fb-emoji-label">{label}</span>
            </button>
          ))}
        </div>

        {rating > 0 && (
          <p className="fb-star-prompt animate-fade-in">{PROMPTS[rating]}</p>
        )}

        {/* Comment */}
        {rating > 0 && (
          <div className="fb-comment-wrap animate-fade-in">
            <textarea
              className="input fb-textarea"
              placeholder="Share more (optional)..."
              value={comment}
              onChange={e => setComment(e.target.value)}
              rows={3}
            />
          </div>
        )}

        <div className="fb-actions">
          <button
            className="btn btn-primary btn-lg fb-cta"
            onClick={handleSubmit}
            disabled={!rating || saving}
          >
            {saving ? 'Submitting...' : rating ? 'Submit Feedback' : 'Pick an emoji first'}
          </button>
          <button className="fb-skip" onClick={onSkip}>Skip</button>
        </div>

      </div>
    </div>
  )
}
