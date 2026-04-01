import React, { useState, useEffect } from 'react';
import { Activity, BookOpen, Terminal, RefreshCw, Zap } from 'lucide-react';
import './index.css';

const API_BASE = 'http://localhost:3000/api';

function App() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchStats = async () => {
    try {
      const res = await fetch(`${API_BASE}/stats`);
      const data = await res.json();
      setStats(data);
    } catch (err) {
      console.error(err);
    }
  };

  const forcePoll = async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/force-poll`, { method: 'POST' });
      const data = await res.json();
      setStats(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 60000); // UI refetch every min
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="app-container">
      <header className="header">
        <div className="header-content">
          <Activity className="header-icon" />
          <h1>Levels Tracker</h1>
        </div>
        <button className={`refresh-btn ${loading ? 'spinning' : ''}`} onClick={forcePoll} title="Force Server Scrape">
          <RefreshCw size={20} />
        </button>
      </header>

      <main className="dashboard">
        
        {/* TOTAL DAILY XP OVERVIEW BOARD */}
        <section className="card highlight-card">
          <div className="card-header highlight-header">
            <Zap size={24} className="card-icon glow" />
            <h2 className="glow">TOTAL DAILY XP</h2>
          </div>
          <div className="card-body">
            {stats ? (
              <div className="stat-group highlight-group grand-total">
                <span className="stat-value total-glow">{stats.totalDailyXp?.toLocaleString() || 0}</span>
                <span className="stat-label">Combined Daily Progress</span>
              </div>
            ) : (
              <div className="skeleton-loader" />
            )}
          </div>
        </section>

        {/* Boot.dev Card */}
        <section className="card bootdev-card">
          <div className="card-header">
            <Terminal className="card-icon" />
            <h2>Boot.dev</h2>
          </div>
          <div className="card-body">
            {stats && stats.bootdev ? (
              <div className="stat-group highlight-group">
                <span className="stat-value highlight">{stats.bootdev.goHours}</span>
                <span className="stat-label">Hours from Go</span>
              </div>
            ) : (
              <div className="skeleton-loader" />
            )}
          </div>
        </section>

        {/* MathAcademy Card */}
        <section className="card math-card">
          <div className="card-header">
            <BookOpen className="card-icon" />
            <h2>MathAcademy</h2>
          </div>
          <div className="card-body">
            {stats && stats.mathacademy ? (
                <div className="stat-group highlight-group">
                  <span className="stat-value highlight">{stats.mathacademy.calc2Hours}</span>
                  <span className="stat-label">Hours away from Calc2</span>
                </div>
            ) : (
              <div className="skeleton-loader" />
            )}
          </div>
        </section>
        
        {stats && (
          <div className="last-updated">
            Last server scrape: {stats.lastPolled ? new Date(stats.lastPolled).toLocaleTimeString() : '...'}
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
