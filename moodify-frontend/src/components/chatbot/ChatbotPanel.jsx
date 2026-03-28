import React, { useState, useRef, useEffect, useCallback } from 'react'
import { sendChatMessage } from '../../api/companionApi'
import { useMood } from '../../context/MoodContext'
import { useSpeechRecognition, useSpeechSynthesis } from '../../hooks/useVoice'
import './ChatbotPanel.css'

const QUICK_ACTIONS = [
  { key: 'CALM_ME',   label: '🌿 Calm Me',   color: '#14b8a6' },
  { key: 'MUSIC',     label: '🎵 Music',      color: '#8b5cf6' },
  { key: 'JOURNAL',   label: '📝 Journal',    color: '#f59e0b' },
  { key: 'PLAY_GAME', label: '🎮 Play Game',  color: '#3b82f6' },
  { key: 'NEED_HELP', label: '💙 Need Help',  color: '#ec4899' },
]

export default function ChatbotPanel({ isOpen, onClose, onMessageSent }) {
  const { currentMood } = useMood()

  const [messages,  setMessages]  = useState([
    { sender: 'BOT', text: "Hey there! I'm Moo, your companion. 🎭 How are you feeling right now? I'm here to listen.", time: new Date() }
  ])
  const [input,     setInput]     = useState('')
  const [loading,   setLoading]   = useState(false)
  const [sessionId, setSessionId] = useState(null)

  // Voice: OFF by default — user must explicitly enable
  const [voiceEnabled, setVoiceEnabled] = useState(false)
  const userHasSentMessage = useRef(false)
  const messagesEndRef = useRef(null)

  const { speaking, supported: ttsSupported, speak, stopSpeaking } = useSpeechSynthesis()

  const handleVoiceResult = useCallback((transcript) => {
    setInput(transcript)
    setTimeout(() => sendMessage(transcript), 400)
  }, []) // eslint-disable-line

  const { listening, supported: sttSupported, startListening, stopListening } =
    useSpeechRecognition({ onResult: handleVoiceResult })

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Speak ONLY when:
  // 1. voiceEnabled is true
  // 2. user has already sent at least one message (not on welcome message)
  // 3. the latest message is from BOT
  useEffect(() => {
    if (!voiceEnabled || !ttsSupported || !userHasSentMessage.current) return
    const last = messages[messages.length - 1]
    if (last?.sender === 'BOT') {
      stopSpeaking()
      speak(last.text)
    }
  }, [messages]) // eslint-disable-line

  // Stop speaking when panel closes or user navigates away
  useEffect(() => {
    if (!isOpen) {
      stopSpeaking()
      stopListening()
    }
  }, [isOpen]) // eslint-disable-line

  // Stop speaking on unmount
  useEffect(() => {
    return () => {
      stopSpeaking()
      stopListening()
    }
  }, []) // eslint-disable-line

  const sendMessage = async (text, quickAction = null) => {
    const msg = (text || quickAction || '').trim()
    if (!msg) return

    userHasSentMessage.current = true
    setMessages(prev => [...prev, { sender: 'USER', text: msg, time: new Date() }])
    setInput('')
    setLoading(true)
    stopSpeaking()
    if (onMessageSent) onMessageSent()

    try {
      const res = await sendChatMessage({ message: msg, sessionId, currentMood, quickAction })
      const data = res.data.data
      if (!sessionId) setSessionId(data.sessionId)
      setMessages(prev => [...prev, {
        sender: 'BOT',
        text: data.message,
        suggestions: data.suggestions,
        time: new Date(),
      }])
    } catch {
      // Meaningful fallback for when the API actually times out or fails
      const fallbacks = [
        "I'm having a little trouble connecting right now, but I'm still here for you. Could you try asking me again in a moment? 💙",
        "My connection seems to be sleepy right now. 🌿 If you wait a tiny bit and try again, I'll be right back with you!",
        "Hmm, my thoughts are loading a bit slowly right now. Feel free to use one of the quick actions below, or pause a second and try again. 😊",
        "I'm right here. It looks like my connection is slow at the moment, but take a deep breath and try sending that again. 💜",
      ]
      setMessages(prev => [...prev, {
        sender: 'BOT',
        text: fallbacks[Math.floor(Math.random() * fallbacks.length)],
        time: new Date(),
      }])
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(input) }
  }

  const toggleVoice = () => {
    if (voiceEnabled) stopSpeaking()
    setVoiceEnabled(v => !v)
  }

  const handleMicClick = () => {
    if (listening) { stopListening(); return }
    stopSpeaking()
    startListening()
  }

  if (!isOpen) return null

  return (
    <div className="chatbot-overlay" onClick={onClose}>
      <div className="chatbot-panel" onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div className="chatbot-header">
          <div className="chatbot-avatar">
            <div className="chatbot-avatar-emoji">🎭</div>
            <div className="chatbot-status-dot" />
          </div>
          <div className="chatbot-header-info">
            <h3 className="chatbot-name">Moo</h3>
            <span className="chatbot-status">
              {listening ? '🎙️ Listening...' : speaking ? '🔊 Speaking...' : 'Your companion · Always here'}
            </span>
          </div>
          <div className="chatbot-header-actions">
            {/* Stop speaking button — only shows when actively speaking */}
            {speaking && (
              <button
                className="chatbot-stop-btn"
                onClick={stopSpeaking}
                title="Stop speaking"
              >
                ⏹
              </button>
            )}
            {ttsSupported && (
              <button
                className={`chatbot-voice-toggle ${voiceEnabled ? 'active' : ''}`}
                onClick={toggleVoice}
                title={voiceEnabled ? 'Turn off voice replies' : 'Turn on voice replies'}
              >
                {voiceEnabled ? '🔊' : '🔇'}
              </button>
            )}
            <button className="chatbot-close" onClick={onClose}>✕</button>
          </div>
        </div>

        {/* Voice mode banner — only when enabled */}
        {voiceEnabled && ttsSupported && (
          <div className="voice-mode-banner">
            <span>🔊 Voice replies on — tap 🔇 to mute</span>
          </div>
        )}

        {/* Quick actions */}
        <div className="chatbot-quick-actions">
          {QUICK_ACTIONS.map(action => (
            <button
              key={action.key}
              className="quick-action-btn"
              style={{ '--action-color': action.color }}
              onClick={() => sendMessage(action.label, action.key)}
            >
              {action.label}
            </button>
          ))}
        </div>

        {/* Messages */}
        <div className="chatbot-messages">
          {messages.map((msg, i) => (
            <div key={i} className={`chat-message ${msg.sender === 'USER' ? 'user' : 'bot'}`}>
              {msg.sender === 'BOT' && <div className="bot-avatar">🎭</div>}
              <div className="message-bubble">
                <p className="message-text">{msg.text}</p>
                <span className="message-time">
                  {msg.time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            </div>
          ))}
          {loading && (
            <div className="chat-message bot">
              <div className="bot-avatar">🎭</div>
              <div className="message-bubble typing">
                <span /><span /><span />
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input area */}
        <div className="chatbot-input-area">
          {sttSupported && (
            <button
              className={`chatbot-mic-btn ${listening ? 'listening' : ''}`}
              onClick={handleMicClick}
              title={listening ? 'Stop listening' : 'Speak to Moo'}
            >
              {listening ? '⏹' : '🎙️'}
            </button>
          )}
          <textarea
            className="chatbot-input"
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={listening ? 'Listening...' : "Share what's on your mind..."}
            rows={1}
            disabled={listening}
          />
          <button
            className="chatbot-send-btn"
            onClick={() => sendMessage(input)}
            disabled={!input.trim() || loading}
          >
            ➤
          </button>
        </div>

      </div>
    </div>
  )
}
