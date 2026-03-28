import React from 'react'
import { useTheme } from '../../context/ThemeContext'
import './ThemeSelector.css'

export default function ThemeSelector({ selected, onSelect }) {
  const { themes } = useTheme()

  return (
    <div className="theme-selector">
      {Object.entries(themes).map(([key, theme]) => (
        <button
          key={key}
          className={`theme-option ${selected === key ? 'selected' : ''}`}
          onClick={() => onSelect(key)}
          title={theme.name}
        >
          <div
            className="theme-preview"
            style={{ background: theme.gradient }}
          >
            {theme.emoji && <span className="theme-emoji">{theme.emoji}</span>}
          </div>
          <span className="theme-name">{theme.name}</span>
          {selected === key && <span className="theme-check">✓</span>}
        </button>
      ))}
    </div>
  )
}
